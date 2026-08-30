package com.apexos.repoguardian.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexos.repoguardian.core.logging.AppLogger

import com.apexos.repoguardian.data.github.ApiResult
import com.apexos.repoguardian.data.github.GitHubRepository
import com.apexos.repoguardian.data.github.models.*
import com.apexos.repoguardian.data.huggingface.ModelDownloadManager
import com.apexos.repoguardian.data.llm.InferenceMetrics
import com.apexos.repoguardian.data.llm.LlamaService
import com.apexos.repoguardian.data.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val isSystem: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val repoContext: String? = null,
    val metrics: InferenceMetrics? = null
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
    val openIssuesCount: Int = 0,
    val rootFiles: List<String> = emptyList(),
    val allFiles: List<String> = emptyList(),
    val readmeExcerpt: String = "",
    val fullReadme: String = "",
    val recentCommits: List<Commit> = emptyList(),
    val openIssues: List<GitHubIssue> = emptyList(),
    val openPulls: List<PullRequest> = emptyList(),
    val lastSyncError: String? = null
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
    val isThinkingModel: Boolean = false,
    val isThinkModeEnabled: Boolean = false,
    val activeModelName: String = "No Model Loaded",
    val showDownloadBanner: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val llamaService: LlamaService,
    private val gitHubRepository: GitHubRepository,
    private val preferencesManager: PreferencesManager,
    private val modelDownloadManager: ModelDownloadManager
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    private var currentGenerationJob: kotlinx.coroutines.Job? = null

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

            // If no model path set or file deleted, auto-select first available downloaded model
            if ((modelPath.isNullOrBlank() || !File(modelPath).exists()) && downloaded.isNotEmpty()) {
                val firstModel = downloaded.first()
                modelPath = firstModel.absolutePath
                preferencesManager.saveModelPath(modelPath)
            }

            val hasModel = !modelPath.isNullOrBlank() && File(modelPath).exists()
            if (hasModel && !llamaService.isLoaded()) {
                val backend = preferencesManager.getBackend()
                val gpuLayers = if (backend == "gpu" || backend == "npu") 33 else 0
                llamaService.loadModel(modelPath!!, gpuLayers)
            }

            val modelName = if (hasModel) File(modelPath!!).nameWithoutExtension else "No Model Loaded"
            val isThinking = if (hasModel) llamaService.isReasoningModel(modelPath!!) else false

            _uiState.value = _uiState.value.copy(
                downloadedModels = downloaded,
                activeModelName = modelName,
                isThinkingModel = isThinking,
                isThinkModeEnabled = isThinking,
                showDownloadBanner = downloaded.isEmpty()
            )

            // Load user repos list from GitHub REST API
            when (val result = gitHubRepository.listRepos()) {
                is ApiResult.Success<List<Repo>> -> {
                    _uiState.value = _uiState.value.copy(availableRepos = result.data)
                }
                is ApiResult.Error -> {
                    Log.w(TAG, "Failed to list repos from GitHub: ${result.message}")
                }
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
            Log.i(TAG, "Initiating live repository data sync via GitHub REST API for $owner/$repo")

            val repoDetailsDeferred = async { gitHubRepository.getRepo(owner, repo) }
            val rootContentsDeferred = async { gitHubRepository.getRootContents(owner, repo) }
            val readmeDeferred = async { gitHubRepository.getReadme(owner, repo) }
            val commitsDeferred = async { gitHubRepository.listCommits(owner, repo, perPage = 15) }
            val gitTreeDeferred = async { gitHubRepository.getGitTree(owner, repo, "HEAD", true) }
            val issuesDeferred = async { gitHubRepository.listIssues(owner, repo, "open", 10) }
            val pullsDeferred = async { gitHubRepository.listPulls(owner, repo, "open", 10) }

            val repoResult = repoDetailsDeferred.await()
            val rootResult = rootContentsDeferred.await()
            val readmeResult = readmeDeferred.await()
            val commitsResult = commitsDeferred.await()
            val treeResult = gitTreeDeferred.await()
            val issuesResult = issuesDeferred.await()
            val pullsResult = pullsDeferred.await()

            val repoDetail = (repoResult as? ApiResult.Success<RepoDetail>)?.data
            val rootContents = (rootResult as? ApiResult.Success<List<DirectoryItem>>)?.data ?: emptyList()
            val readme = (readmeResult as? ApiResult.Success<String>)?.data ?: ""
            val commits = (commitsResult as? ApiResult.Success<List<Commit>>)?.data ?: emptyList()
            val treeItems = (treeResult as? ApiResult.Success<GitTreeResponse>)?.data?.tree ?: emptyList()
            val openIssues = (issuesResult as? ApiResult.Success<List<GitHubIssue>>)?.data ?: emptyList()
            val openPulls = (pullsResult as? ApiResult.Success<List<PullRequest>>)?.data ?: emptyList()

            val allFilePaths = if (treeItems.isNotEmpty()) {
                treeItems.filter { it.type == "blob" }.map { it.path }
            } else {
                rootContents.map { it.name }
            }

            var errorMsg: String? = null
            if (repoResult is ApiResult.Error) {
                errorMsg = repoResult.message
                Log.e(TAG, "GitHub API sync error for $owner/$repo: $errorMsg")
            }

            val liveCtx = LiveRepoContext(
                owner = owner,
                name = repo,
                description = repoDetail?.description ?: "",
                language = repoDetail?.language ?: "",
                defaultBranch = repoDetail?.defaultBranch ?: "main",
                stars = repoDetail?.stargazersCount ?: 0,
                forks = repoDetail?.forksCount ?: 0,
                openIssuesCount = repoDetail?.openIssuesCount ?: 0,
                rootFiles = rootContents.map { it.name },
                allFiles = allFilePaths,
                readmeExcerpt = readme.take(800),
                fullReadme = readme,
                recentCommits = commits,
                openIssues = openIssues,
                openPulls = openPulls,
                lastSyncError = errorMsg
            )

            _uiState.value = _uiState.value.copy(
                liveRepoContext = liveCtx,
                repoOwner = owner,
                repoName = repo,
                error = if (errorMsg != null) "GitHub Sync Notice: $errorMsg" else null
            )

            Log.i(
                TAG,
                "GitHub repository sync completed: $owner/$repo | Files: ${allFilePaths.size} | Commits: ${commits.size} | Issues: ${openIssues.size} | PRs: ${openPulls.size}"
            )

            // Initial welcome message with live data
            val langText = if (liveCtx.language.isNotBlank()) " [${liveCtx.language}]" else ""
            val descText = if (liveCtx.description.isNotBlank()) "\n> ${liveCtx.description}" else ""
            val fileCountText = if (liveCtx.allFiles.isNotEmpty()) "${liveCtx.allFiles.size} files indexed" else "${liveCtx.rootFiles.size} root items"
            val commitCountText = "${liveCtx.recentCommits.size} commits fetched"

            val welcome = if (liveCtx.lastSyncError != null) {
                """
### Repo Guardian AI Assistant

⚠️ **GitHub API Notice for $owner/$repo**:
${liveCtx.lastSyncError}

Please verify your GitHub credentials in Settings or ensure the repository is accessible.
                """.trimIndent()
            } else {
                """
### Repo Guardian AI Assistant

Connected to repository **$owner/$repo**$langText.
$descText

Live repository data loaded from GitHub REST API ($fileCountText, $commitCountText, default branch: `${liveCtx.defaultBranch}`). Select a quick prompt below or ask any question about this codebase.
                """.trimIndent()
            }

            _uiState.value = _uiState.value.copy(
                messages = listOf(ChatMessage(content = welcome, isUser = false, isSystem = false))
            )
        }
    }

    fun toggleThinkMode() {
        // Think mode is user-controllable for any model.
        // On reasoning models (DeepSeek-R1, QwQ, etc.) it enables <think>…</think> chains.
        // On standard models the instruction is harmless — model simply won't produce think tags.
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
            val isThinking = llamaService.isReasoningModel(path)

            _uiState.value = _uiState.value.copy(
                activeModelName = modelName,
                isThinkingModel = isThinking,
                isThinkModeEnabled = isThinking,
                showDownloadBanner = false,
                isModelDropdownOpen = false
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

        currentGenerationJob = viewModelScope.launch {
            val downloaded = modelDownloadManager.getDownloadedModels()
            var modelPath = preferencesManager.getModelPath()
            val localServerUrl = preferencesManager.getLocalServerUrl().trim()

            // 1. If no model is downloaded and no local server URL is set
            if (downloaded.isEmpty() && (modelPath.isNullOrBlank() || !File(modelPath).exists()) && localServerUrl.isBlank()) {
                val errMsg = "No model downloaded and no Local Server configured. Download a GGUF model or add a Local Server URL in Settings."
                AppLogger.w(TAG, errMsg)
                _uiState.value = _uiState.value.copy(
                    showDownloadBanner = true,
                    error = errMsg
                )
                return@launch
            }

            // 2. If model is downloaded but not loaded and no local server URL is set, load it
            if (!llamaService.isLoaded() && localServerUrl.isBlank()) {
                val targetPath = if (!modelPath.isNullOrBlank() && File(modelPath).exists()) {
                    modelPath
                } else if (downloaded.isNotEmpty()) {
                    downloaded.first().absolutePath.also {
                        preferencesManager.saveModelPath(it)
                    }
                } else null

                if (targetPath != null) {
                    val backend = preferencesManager.getBackend()
                    val gpuLayers = if (backend == "gpu" || backend == "npu") 33 else 0
                    AppLogger.i(TAG, "Auto-loading model before chat: $targetPath (layers: $gpuLayers)")
                    llamaService.loadModel(targetPath, gpuLayers)

                    // Sync model identity into UI state so think-mode toggle reflects this model
                    val isThinking = llamaService.isReasoningModel(targetPath)
                    val modelName = File(targetPath).nameWithoutExtension
                    _uiState.value = _uiState.value.copy(
                        activeModelName = modelName,
                        isThinkingModel = isThinking,
                        // Preserve user's manual toggle if they already changed it; otherwise default to model capability
                        isThinkModeEnabled = if (_uiState.value.activeModelName == "No Model Loaded") isThinking else _uiState.value.isThinkModeEnabled
                    )
                }
            }

            if (!llamaService.isLoaded() && localServerUrl.isBlank()) {
                val errMsg = "The downloaded model could not be loaded into memory. Check Live Dev Logs in Settings for error details."
                AppLogger.e(TAG, errMsg)
                _uiState.value = _uiState.value.copy(error = errMsg)
                return@launch
            }

            val aiMessageId = UUID.randomUUID().toString()
            val initialAiMessage = ChatMessage(id = aiMessageId, content = "", isUser = false, isSystem = false)

            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + userMessage + initialAiMessage,
                isGenerating = true,
                error = null
            )

            val startTime = System.currentTimeMillis()
            var tokensCount = 0
            val backendPref = preferencesManager.getBackend().lowercase()
            val activeBackend = when {
                localServerUrl.isNotBlank() -> "Local Server ($localServerUrl)"
                backendPref == "npu" -> "Snapdragon NPU (Hexagon)"
                backendPref == "gpu" -> "Adreno GPU (Vulkan)"
                else -> "CPU (ARM NEON)"
            }

            try {
                val ctx = _uiState.value.liveRepoContext
                val owner = ctx.owner.ifBlank { _uiState.value.repoOwner }
                val repo = ctx.name.ifBlank { _uiState.value.repoName }

                AppLogger.i(TAG, "Preparing context for user prompt: \"$userText\" on $owner/$repo")
                // Retrieve real source files, diffs, and context from GitHub REST API tailored to user question
                val dynamicGitHubData = retrieveDynamicGitHubContext(owner, repo, userText, ctx)

                val commitsSummary = if (ctx.recentCommits.isNotEmpty()) {
                    ctx.recentCommits.take(8).joinToString("\n") {
                        val msg = it.commit.message.lines().firstOrNull() ?: ""
                        val author = it.commit.author?.name ?: "Author"
                        val date = it.commit.author?.date?.take(10) ?: ""
                        "- [${it.sha.take(7)}] $msg ($author, $date)"
                    }
                } else {
                    "No recent commits recorded"
                }

                val fileTreeSection = if (ctx.allFiles.isNotEmpty()) {
                    val shown = ctx.allFiles.take(25).joinToString("\n") { "  - $it" }
                    val extra = if (ctx.allFiles.size > 25) "\n  ... (${ctx.allFiles.size - 25} more files)" else ""
                    "Repository File Tree (${ctx.allFiles.size} total files):\n$shown$extra"
                } else if (ctx.rootFiles.isNotEmpty()) {
                    "Root Files:\n" + ctx.rootFiles.joinToString("\n") { "  - $it" }
                } else {
                    "Repository file tree not loaded"
                }

                val errorNotice = if (!ctx.lastSyncError.isNullOrBlank()) {
                    "\n[GitHub API Notice: ${ctx.lastSyncError}]\n"
                } else ""

                val systemPrompt = """
Active Repository: $owner/$repo
Description: ${ctx.description.take(150)}
Language: ${ctx.language} | Default Branch: ${ctx.defaultBranch}
Stars: ${ctx.stars} | Open Issues: ${ctx.openIssuesCount}
$errorNotice
$fileTreeSection

README:
${ctx.readmeExcerpt.take(400)}

Recent Commits:
$commitsSummary

$dynamicGitHubData
                """.trimIndent()

                AppLogger.d(TAG, "Passing system context to LLM (${systemPrompt.length} chars)")

                val accumulatedResponse = StringBuilder()

                llamaService.chatStream(
                    userMessage = userText.trim(),
                    systemPrompt = systemPrompt,
                    isThinkMode = _uiState.value.isThinkingModel && _uiState.value.isThinkModeEnabled
                ).collect { token ->
                    tokensCount++
                    accumulatedResponse.append(token)
                    val currentText = accumulatedResponse.toString()

                    val updatedMessages = _uiState.value.messages.map { msg ->
                        if (msg.id == aiMessageId) {
                            msg.copy(content = currentText)
                        } else {
                            msg
                        }
                    }
                    _uiState.value = _uiState.value.copy(messages = updatedMessages)
                }

                val elapsedMs = System.currentTimeMillis() - startTime
                val tps = if (elapsedMs > 0) (tokensCount.toDouble() / (elapsedMs / 1000.0)) else 0.0
                val metrics = InferenceMetrics(
                    totalTimeMs = elapsedMs,
                    tokenCount = tokensCount,
                    tokensPerSecond = tps,
                    backend = activeBackend
                )
                val finalMessages = _uiState.value.messages.map { msg ->
                    if (msg.id == aiMessageId) {
                        msg.copy(metrics = metrics)
                    } else {
                        msg
                    }
                }

                _uiState.value = _uiState.value.copy(
                    messages = finalMessages,
                    isGenerating = false
                )
                AppLogger.i(TAG, "Chat generation finished ($tokensCount tokens, ${elapsedMs}ms, ${String.format("%.1f", tps)} tok/s, backend: $activeBackend)")
            } catch (c: kotlinx.coroutines.CancellationException) {
                AppLogger.i(TAG, "Chat generation stopped by user request")
                val currentText = _uiState.value.messages.find { it.id == aiMessageId }?.content ?: ""
                val elapsedMs = System.currentTimeMillis() - startTime
                val tps = if (elapsedMs > 0) (tokensCount.toDouble() / (elapsedMs / 1000.0)) else 0.0
                val metrics = InferenceMetrics(
                    totalTimeMs = elapsedMs,
                    tokenCount = tokensCount,
                    tokensPerSecond = tps,
                    backend = "$activeBackend (Stopped)"
                )
                val updatedMessages = _uiState.value.messages.map { msg ->
                    if (msg.id == aiMessageId) {
                        msg.copy(content = currentText, metrics = metrics)
                    } else {
                        msg
                    }
                }
                _uiState.value = _uiState.value.copy(
                    messages = updatedMessages,
                    isGenerating = false
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Inference execution error in ChatViewModel", e)
                val errorMsg = "Inference error: ${e.localizedMessage ?: e.message}"
                val currentText = _uiState.value.messages.find { it.id == aiMessageId }?.content ?: ""
                val finalText = if (currentText.isNotBlank()) "$currentText\n\n*(Inference stopped: $errorMsg)*" else "⚠️ **Inference Error:** ${e.localizedMessage ?: e.message}\n\nPlease check Dev Logs in Settings or verify your model/local server."

                val updatedMessages = _uiState.value.messages.map { msg ->
                    if (msg.id == aiMessageId) {
                        msg.copy(content = finalText)
                    } else {
                        msg
                    }
                }

                _uiState.value = _uiState.value.copy(
                    messages = updatedMessages,
                    isGenerating = false,
                    error = errorMsg
                )
            }
        }
    }

    fun stopGeneration() {
        if (_uiState.value.isGenerating) {
            AppLogger.i(TAG, "User pressed stop button to cancel AI generation")
            currentGenerationJob?.cancel()
            _uiState.value = _uiState.value.copy(isGenerating = false)
        }
    }


    private suspend fun retrieveDynamicGitHubContext(
        owner: String,
        repo: String,
        userText: String,
        liveCtx: LiveRepoContext
    ): String = withContext(Dispatchers.IO) {
        val retrievedBuilder = StringBuilder()
        val lowerText = userText.lowercase()

        AppLogger.d(TAG, "Extracting relevant GitHub repository sources for prompt: \"$userText\"")

        // 1. Identify specific file mentions from prompt
        val matchedFiles = mutableSetOf<String>()

        // Check each indexed file path in repository tree
        for (filePath in liveCtx.allFiles) {
            val fileName = filePath.substringAfterLast('/')
            if (userText.contains(filePath, ignoreCase = true) ||
                (fileName.length >= 4 && userText.contains(fileName, ignoreCase = true))) {
                matchedFiles.add(filePath)
            }
        }

        // Keyword-based relevant file identification if no exact file was mentioned
        if (matchedFiles.isEmpty()) {
            val topicKeywords = listOf(
                listOf("auth", "login", "token", "credential", "security") to listOf("Auth", "Token", "User", "Security"),
                listOf("chat", "conversation", "message", "prompt") to listOf("Chat", "Prompt", "Message"),
                listOf("llm", "llama", "bridge", "reasoning", "model", "inference") to listOf("Llama", "Bridge", "AiReasoning", "Model"),
                listOf("voice", "speech", "audio", "mic") to listOf("Voice", "Audio", "Speech"),
                listOf("cicd", "workflow", "action", "pipeline", ".github") to listOf("CiCd", "Workflow", "build.gradle", "package.json"),
                listOf("build", "gradle", "dependencies", "manifest", "pom") to listOf("build.gradle", "settings.gradle", "pom.xml", "package.json", "AndroidManifest"),
                listOf("network", "retrofit", "okhttp", "service", "api", "http") to listOf("GitHubService", "GitHubRepository", "AppModule", "Api"),
                listOf("navigation", "nav", "route", "screen") to listOf("NavGraph", "MainActivity", "Screen"),
                listOf("review", "diff", "patch") to listOf("Review", "Diff", "Commit")
            )

            for ((triggers, terms) in topicKeywords) {
                if (triggers.any { lowerText.contains(it) }) {
                    for (term in terms) {
                        val matching = liveCtx.allFiles.filter { it.contains(term, ignoreCase = true) }
                        matchedFiles.addAll(matching.take(2))
                    }
                }
            }
        }

        // If broad question about architecture / overview / explain:
        if (matchedFiles.isEmpty() && (
            lowerText.contains("explain") ||
            lowerText.contains("architecture") ||
            lowerText.contains("structure") ||
            lowerText.contains("modules") ||
            lowerText.contains("overview") ||
            lowerText.contains("design")
        )) {
            val manifests = liveCtx.allFiles.filter { path ->
                path.endsWith("build.gradle.kts") ||
                path.endsWith("build.gradle") ||
                path.endsWith("package.json") ||
                path.endsWith("pom.xml") ||
                path.endsWith("Cargo.toml") ||
                path.endsWith("requirements.txt") ||
                path.endsWith("CMakeLists.txt") ||
                path.endsWith("AndroidManifest.xml")
            }
            matchedFiles.addAll(manifests.take(2))
        }

        // Retrieve real source code from GitHub REST API
        val filesToFetch = matchedFiles.take(2)
        if (filesToFetch.isNotEmpty()) {
            AppLogger.i(TAG, "Retrieving ${filesToFetch.size} real source file(s) from GitHub REST API: $filesToFetch")
            retrievedBuilder.appendLine("=== [RETRIEVED REAL GITHUB SOURCE CODE] ===")
            for (path in filesToFetch) {
                when (val fileResult = gitHubRepository.getFileText(owner, repo, path, ref = liveCtx.defaultBranch)) {
                    is ApiResult.Success -> {
                        val content = fileResult.data
                        val truncatedContent = if (content.length > 1200) {
                            content.take(1200) + "\n... [Content truncated]"
                        } else {
                            content
                        }
                        val ext = path.substringAfterLast('.', "text")
                        retrievedBuilder.appendLine("#### Source File: `$path`")
                        retrievedBuilder.appendLine("```$ext")
                        retrievedBuilder.appendLine(truncatedContent)
                        retrievedBuilder.appendLine("```")
                        retrievedBuilder.appendLine()
                    }
                    is ApiResult.Error -> {
                        AppLogger.w(TAG, "GitHub REST API could not fetch $path: ${fileResult.message}")
                        retrievedBuilder.appendLine("#### Source File: `$path` - [GitHub Retrieval Error: ${fileResult.message}]")
                        retrievedBuilder.appendLine()
                    }
                }
            }
        }

        // 2. Commit diff retrieval for commit / review / diff / security queries
        if (lowerText.contains("commit") ||
            lowerText.contains("review") ||
            lowerText.contains("diff") ||
            lowerText.contains("security") ||
            lowerText.contains("vulnerability") ||
            lowerText.contains("recent change")
        ) {
            val topCommits = liveCtx.recentCommits.take(2)
            if (topCommits.isNotEmpty()) {
                retrievedBuilder.appendLine("=== [RETRIEVED LIVE COMMIT DIFFS FROM GITHUB] ===")
                for (c in topCommits) {
                    AppLogger.i(TAG, "Retrieving commit diff from GitHub REST API for sha=${c.sha}")
                    when (val diffResult = gitHubRepository.getCommitDiff(owner, repo, c.sha)) {
                        is ApiResult.Success -> {
                            val diff = diffResult.data
                            val msg = c.commit.message.lines().firstOrNull() ?: ""
                            val author = c.commit.author?.name ?: "Unknown"
                            retrievedBuilder.appendLine("#### Commit ${c.sha.take(7)}: \"$msg\" (by $author)")
                            diff.files?.take(2)?.forEach { file ->
                                retrievedBuilder.appendLine("- Changed file: `${file.filename}` (+${file.additions} -${file.deletions})")
                                if (!file.patch.isNullOrBlank()) {
                                    val truncatedPatch = if (file.patch.length > 600) {
                                        file.patch.take(600) + "\n... (diff truncated)"
                                    } else {
                                        file.patch
                                    }
                                    retrievedBuilder.appendLine("```diff")
                                    retrievedBuilder.appendLine(truncatedPatch)
                                    retrievedBuilder.appendLine("```")
                                }
                            }
                            retrievedBuilder.appendLine()
                        }
                        is ApiResult.Error -> {
                            retrievedBuilder.appendLine("#### Commit ${c.sha.take(7)}: [Diff Retrieval Error: ${diffResult.message}]")
                            retrievedBuilder.appendLine()
                        }
                    }
                }
            }
        }


        // 3. Issues & PRs inspection
        if (lowerText.contains("issue") ||
            lowerText.contains("pr") ||
            lowerText.contains("pull request") ||
            lowerText.contains("bug") ||
            lowerText.contains("milestone")
        ) {
            if (liveCtx.openIssues.isNotEmpty()) {
                retrievedBuilder.appendLine("=== [RETRIEVED GITHUB OPEN ISSUES] ===")
                liveCtx.openIssues.take(5).forEach { issue ->
                    val bodyExcerpt = issue.body?.take(200)?.replace("\n", " ") ?: "No description"
                    retrievedBuilder.appendLine("- #${issue.number}: **${issue.title}** (by @${issue.user?.login ?: "unknown"}) - $bodyExcerpt")
                }
                retrievedBuilder.appendLine()
            }
            if (liveCtx.openPulls.isNotEmpty()) {
                retrievedBuilder.appendLine("=== [RETRIEVED GITHUB OPEN PULL REQUESTS] ===")
                liveCtx.openPulls.take(5).forEach { pr ->
                    retrievedBuilder.appendLine("- #${pr.number}: **${pr.title}** [branch: `${pr.head?.ref}` -> `${pr.base?.ref}`]")
                }
                retrievedBuilder.appendLine()
            }
        }

        retrievedBuilder.toString()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
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

