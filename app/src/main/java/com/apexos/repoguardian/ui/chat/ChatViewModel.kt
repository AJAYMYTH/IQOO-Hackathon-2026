package com.apexos.repoguardian.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexos.repoguardian.data.github.ApiResult
import com.apexos.repoguardian.data.github.GitHubRepository
import com.apexos.repoguardian.data.github.models.Commit
import com.apexos.repoguardian.data.github.models.DirectoryItem
import com.apexos.repoguardian.data.github.models.Repo
import com.apexos.repoguardian.data.github.models.RepoDetail
import com.apexos.repoguardian.data.huggingface.ModelDownloadManager
import com.apexos.repoguardian.data.llm.LlamaService
import com.apexos.repoguardian.data.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
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

data class LiveRepoContext(
    val owner: String = "",
    val name: String = "",
    val description: String = "",
    val language: String = "",
    val defaultBranch: String = "main",
    val stars: Int = 0,
    val forks: Int = 0,
    val rootFiles: List<String> = emptyList(),
    val readmeExcerpt: String = "",
    val recentCommits: List<Commit> = emptyList()
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val repoOwner: String = "",
    val repoName: String = "",
    val liveRepoContext: LiveRepoContext = LiveRepoContext(),
    val availableRepos: List<Repo> = emptyList(),
    val isRepoDropdownOpen: Boolean = false,
    val downloadedModels: List<File> = emptyList(),
    val isModelDropdownOpen: Boolean = false,
    val isThinkModeEnabled: Boolean = true,
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
            prompt = "Generate a production-ready GitHub Actions CI/CD workflow tailored for this repository with automated testing, lint checks, and artifact packaging."
        ),
        QuickPrompt(
            title = "Write Unit Tests",
            category = PromptCategory.TESTS,
            prompt = "Write comprehensive unit tests tailored to the primary language and components of this repository."
        ),
        QuickPrompt(
            title = "Security Audit",
            category = PromptCategory.SECURITY,
            prompt = "Perform a security audit on this repository. Check dependencies, branch protection, and credential hygiene."
        ),
        QuickPrompt(
            title = "Optimize Performance",
            category = PromptCategory.PERFORMANCE,
            prompt = "Analyze potential performance bottlenecks in runtime execution, memory allocation, and build speed, and suggest optimization strategies."
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

            // If no model path set, but downloaded models exist, auto-select first one
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

            // Load user repos list
            when (val result = gitHubRepository.listRepos()) {
                is ApiResult.Success<List<Repo>> -> {
                    _uiState.value = _uiState.value.copy(availableRepos = result.data)
                }
                else -> {}
            }

            if (selectedRepo != null) {
                val owner = selectedRepo.first
                val repo = selectedRepo.second
                _uiState.value = _uiState.value.copy(repoOwner = owner, repoName = repo)
                loadLiveRepositoryData(owner, repo)
            } else if (_uiState.value.availableRepos.isNotEmpty()) {
                val first = _uiState.value.availableRepos.first()
                _uiState.value = _uiState.value.copy(repoOwner = first.owner.login, repoName = first.name)
                loadLiveRepositoryData(first.owner.login, first.name)
            }
        }
    }

    private fun loadLiveRepositoryData(owner: String, repo: String) {
        viewModelScope.launch {
            val repoDetailsDeferred = async { gitHubRepository.getRepo(owner, repo) }
            val rootContentsDeferred = async { gitHubRepository.getRootContents(owner, repo) }
            val readmeDeferred = async { gitHubRepository.getReadme(owner, repo) }
            val commitsDeferred = async { gitHubRepository.listCommits(owner, repo) }

            val repoDetail = (repoDetailsDeferred.await() as? ApiResult.Success<RepoDetail>)?.data
            val rootContents = (rootContentsDeferred.await() as? ApiResult.Success<List<DirectoryItem>>)?.data ?: emptyList()
            val readme = (readmeDeferred.await() as? ApiResult.Success<String>)?.data ?: ""
            val commits = (commitsDeferred.await() as? ApiResult.Success<List<Commit>>)?.data ?: emptyList()

            val liveCtx = LiveRepoContext(
                owner = owner,
                name = repo,
                description = repoDetail?.description ?: "",
                language = repoDetail?.language ?: "",
                defaultBranch = repoDetail?.defaultBranch ?: "main",
                stars = repoDetail?.stargazersCount ?: 0,
                forks = repoDetail?.forksCount ?: 0,
                rootFiles = rootContents.map { it.name },
                readmeExcerpt = readme.take(500),
                recentCommits = commits.take(6)
            )

            _uiState.value = _uiState.value.copy(
                liveRepoContext = liveCtx,
                repoOwner = owner,
                repoName = repo
            )

            // Initial welcome message with live data
            val langText = if (liveCtx.language.isNotBlank()) " [${liveCtx.language}]" else ""
            val descText = if (liveCtx.description.isNotBlank()) "\n> ${liveCtx.description}" else ""
            val welcome = """
### Repo Guardian AI Assistant

Connected to repository **$owner/$repo**$langText.
$descText

Live repository metadata and root files loaded from GitHub. Select a quick prompt below or ask any question about this codebase.
            """.trimIndent()

            _uiState.value = _uiState.value.copy(
                messages = listOf(ChatMessage(content = welcome, isUser = false))
            )
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
            loadLiveRepositoryData(repo.owner.login, repo.name)
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
                val ctx = _uiState.value.liveRepoContext
                val commitsSummary = if (ctx.recentCommits.isNotEmpty()) {
                    ctx.recentCommits.joinToString("\n") {
                        val msg = it.commit.message.lines().firstOrNull() ?: ""
                        val author = it.commit.author?.name ?: "Author"
                        "- ${it.sha.take(7)}: $msg (by $author)"
                    }
                } else {
                    "No recent commits recorded"
                }

                val systemPrompt = """
Active Repository: ${ctx.owner}/${ctx.name}
Description: ${ctx.description}
Language: ${ctx.language}
Default Branch: ${ctx.defaultBranch}
Root Files: ${ctx.rootFiles.joinToString(", ")}
README Excerpt: ${ctx.readmeExcerpt.replace("\n", " ")}
Recent Commits:
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
        val owner = _uiState.value.repoOwner
        val repo = _uiState.value.repoName
        val welcome = "Chat history cleared. How can I assist you with repository **$owner/$repo**?"
        _uiState.value = _uiState.value.copy(
            messages = listOf(ChatMessage(content = welcome, isUser = false)),
            error = null
        )
    }
}
