package com.apexos.repoguardian.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexos.repoguardian.data.github.ApiResult
import com.apexos.repoguardian.data.github.GitHubRepository
import com.apexos.repoguardian.data.github.models.Commit
import com.apexos.repoguardian.data.github.models.Repo
import com.apexos.repoguardian.data.huggingface.ModelDownloadManager
import com.apexos.repoguardian.data.llm.LlamaService
import com.apexos.repoguardian.data.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val repoContext: String? = null
)

enum class PromptCategory {
    EXPLAIN,
    REVIEW,
    CICD,
    TESTS,
    RELEASE,
    SECURITY,
    PERFORMANCE
}

data class QuickPrompt(
    val title: String,
    val category: PromptCategory,
    val prompt: String
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val repoOwner: String = "",
    val repoName: String = "",
    val availableRepos: List<Repo> = emptyList(),
    val isRepoDropdownOpen: Boolean = false,
    val downloadedModels: List<File> = emptyList(),
    val isModelDropdownOpen: Boolean = false,
    val isThinkModeEnabled: Boolean = true,
    val recentCommits: List<Commit> = emptyList(),
    val activeModelName: String = "No Model Loaded",
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val llamaService: LlamaService,
    private val gitHubRepository: GitHubRepository,
    private val preferencesManager: PreferencesManager,
    private val modelDownloadManager: ModelDownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    val quickPrompts = listOf(
        QuickPrompt(
            title = "Explain Repo",
            category = PromptCategory.EXPLAIN,
            prompt = "Explain the architecture, structure, key modules, and technical design of this repository in detail."
        ),
        QuickPrompt(
            title = "Review Commits",
            category = PromptCategory.REVIEW,
            prompt = "Review the recent commits in this repository for potential logic errors, null pointer dereferences, security vulnerabilities, and memory leaks. Provide detailed explanations and actionable code fixes."
        ),
        QuickPrompt(
            title = "Generate CI/CD",
            category = PromptCategory.CICD,
            prompt = "Generate a production-ready GitHub Actions CI/CD workflow (.github/workflows/ci.yml) tailored for this repository with automated testing, lint checks, and artifact packaging."
        ),
        QuickPrompt(
            title = "Write Unit Tests",
            category = PromptCategory.TESTS,
            prompt = "Write comprehensive unit tests with MockK, JUnit 5, and Kotlin Coroutines Test for the primary ViewModel and Repository components of this repository."
        ),
        QuickPrompt(
            title = "Release Pipeline",
            category = PromptCategory.RELEASE,
            prompt = "Create an automated GitHub Actions release workflow (.github/workflows/release.yml) triggered on version tags (v*) that compiles release APKs, generates SHA-256 checksums, and publishes a GitHub Release with changelogs."
        ),
        QuickPrompt(
            title = "Security Audit",
            category = PromptCategory.SECURITY,
            prompt = "Perform a security audit on this repository. Check AndroidManifest permissions, API token handling, and networking configurations."
        ),
        QuickPrompt(
            title = "Optimize Performance",
            category = PromptCategory.PERFORMANCE,
            prompt = "Analyze potential performance bottlenecks in Compose recompositions, coroutine lifecycle scopes, and background threads, and suggest optimization strategies."
        )
    )

    init {
        loadInitialContext()
    }

    fun loadInitialContext() {
        viewModelScope.launch {
            val selectedRepo = preferencesManager.getSelectedRepo()
            val downloaded = modelDownloadManager.getDownloadedModels()
            var modelPath = preferencesManager.getModelPath()

            // If no model path set, but downloaded models exist, auto-select the first one
            if (modelPath.isNullOrBlank() && downloaded.isNotEmpty()) {
                val firstModel = downloaded.first()
                modelPath = firstModel.absolutePath
                preferencesManager.saveModelPath(modelPath)
                llamaService.loadModel(modelPath)
            }

            val modelName = if (!modelPath.isNullOrBlank()) {
                File(modelPath).nameWithoutExtension
            } else {
                "No Model Loaded"
            }

            _uiState.value = _uiState.value.copy(
                downloadedModels = downloaded,
                activeModelName = modelName
            )

            if (selectedRepo != null) {
                _uiState.value = _uiState.value.copy(
                    repoOwner = selectedRepo.first,
                    repoName = selectedRepo.second
                )
                loadRepoCommits(selectedRepo.first, selectedRepo.second)
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
### Repo Guardian AI Assistant

Connected to repository `$owner/$repo` with model `$modelName`.

You can ask questions about your code, request deep repository explanations, perform security commit audits, and generate CI/CD pipelines and unit test suites.

Select any quick action below or type a query to begin.
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

    fun toggleThinkMode() {
        _uiState.value = _uiState.value.copy(isThinkModeEnabled = !_uiState.value.isThinkModeEnabled)
    }

    fun setRepoDropdownOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(isRepoDropdownOpen = open)
    }

    fun setModelDropdownOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(isModelDropdownOpen = open)
    }

    fun switchModel(modelFile: File) {
        viewModelScope.launch {
            val path = modelFile.absolutePath
            val backend = preferencesManager.getBackend()
            val gpuLayers = if (backend == "gpu" || backend == "npu") 33 else 0

            llamaService.unload()
            llamaService.loadModel(path, gpuLayers)
            preferencesManager.saveModelPath(path)

            val modelName = modelFile.nameWithoutExtension
            _uiState.value = _uiState.value.copy(
                activeModelName = modelName,
                isModelDropdownOpen = false
            )

            val notice = "Switched active AI model to **$modelName**."
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + ChatMessage(content = notice, isUser = false)
            )
        }
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

            val switchNotice = "Switched active repository context to **${repo.fullName}**."
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
Active Repository: ${_uiState.value.repoOwner}/${_uiState.value.repoName}
Recent Commits in Context:
$commitsSummary
                """.trimIndent()

                val response = llamaService.chat(
                    userMessage = userText.trim(),
                    systemPrompt = systemPrompt,
                    isThinkMode = _uiState.value.isThinkModeEnabled
                )
                val aiMessage = ChatMessage(content = response, isUser = false)

                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + aiMessage,
                    isGenerating = false
                )
            } catch (e: Exception) {
                val errorMsg = "AI generation error: ${e.localizedMessage ?: e.message}"
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + ChatMessage(content = "Error: $errorMsg", isUser = false),
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
