package com.apexos.repoguardian.ui.cicd

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexos.repoguardian.core.logging.AppLogger
import com.apexos.repoguardian.data.github.ApiResult
import com.apexos.repoguardian.data.github.GitHubRepository
import com.apexos.repoguardian.data.llm.LlamaService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CiCdUiState(
    val owner: String = "",
    val repo: String = "",
    val detectedLanguage: String? = null,
    val generatedYaml: String = "",
    val isGenerating: Boolean = false,
    val isCommitting: Boolean = false,
    val committed: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CiCdGeneratorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val llamaService: LlamaService,
    private val gitHubRepository: GitHubRepository
) : ViewModel() {

    companion object {
        private const val TAG = "CiCdGenerator"
    }

    private val owner: String = savedStateHandle["owner"] ?: ""
    private val repo: String = savedStateHandle["repo"] ?: ""

    private val _uiState = MutableStateFlow(CiCdUiState(
        owner = owner,
        repo = repo
    ))
    val uiState: StateFlow<CiCdUiState> = _uiState

    init {
        detectAndGenerate()
    }

    private fun detectAndGenerate() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true, error = null)
            AppLogger.i(TAG, "Starting CI/CD detection and generation for $owner/$repo")
            try {
                // 1. Get real repo info from GitHub REST API
                val repoResult = gitHubRepository.getRepo(owner, repo)
                val language = (repoResult as? ApiResult.Success)?.data?.language

                // 2. Discover real build files from repository
                val rootContentsResult = gitHubRepository.getRootContents(owner, repo)
                val rootItems = (rootContentsResult as? ApiResult.Success)?.data ?: emptyList()
                val rootNames = rootItems.map { it.name }

                val candidateBuildFiles = listOf(
                    "build.gradle.kts",
                    "build.gradle",
                    "package.json",
                    "pom.xml",
                    "Cargo.toml",
                    "requirements.txt",
                    "pyproject.toml",
                    "CMakeLists.txt",
                    "Makefile"
                )

                val matchedBuildFile = candidateBuildFiles.firstOrNull { file ->
                    rootNames.any { it.equals(file, ignoreCase = true) }
                }

                val buildFileContent = if (matchedBuildFile != null) {
                    when (val fileTextRes = gitHubRepository.getFileText(owner, repo, matchedBuildFile)) {
                        is ApiResult.Success -> "File: $matchedBuildFile\n```\n${fileTextRes.data.take(2000)}\n```"
                        else -> "Build file $matchedBuildFile detected in root."
                    }
                } else {
                    "Root items: ${rootNames.take(15).joinToString(", ")}"
                }

                val detectedLang = language ?: when {
                    rootNames.any { it.contains("gradle") } -> "Kotlin / Android"
                    rootNames.any { it.contains("package.json") } -> "JavaScript / TypeScript"
                    rootNames.any { it.contains("pom.xml") } -> "Java"
                    rootNames.any { it.contains("requirements.txt") || it.contains("pyproject.toml") } -> "Python"
                    rootNames.any { it.contains("Cargo.toml") } -> "Rust"
                    else -> "Unknown"
                }

                _uiState.value = _uiState.value.copy(detectedLanguage = detectedLang)
                AppLogger.i(TAG, "Detected stack for CI/CD: $detectedLang (manifest: $matchedBuildFile)")

                // 3. Generate YAML using real build context
                val yaml = llamaService.generateCiCdYaml(detectedLang, repo, buildFileContent)
                _uiState.value = _uiState.value.copy(
                    generatedYaml = yaml,
                    isGenerating = false
                )
                AppLogger.i(TAG, "CI/CD YAML generated successfully (${yaml.length} chars)")
            } catch (e: Exception) {
                AppLogger.e(TAG, "CI/CD generation failed", e)
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    error = "Generation failed: ${e.localizedMessage ?: e.message}"
                )
            }
        }
    }



    fun updateYaml(yaml: String) {
        _uiState.value = _uiState.value.copy(generatedYaml = yaml)
    }

    fun commitWorkflow() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCommitting = true, error = null)
            when (val result = gitHubRepository.commitWorkflowFile(
                owner = owner,
                repo = repo,
                yamlContent = _uiState.value.generatedYaml,
                workflowName = "ci"
            )) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isCommitting = false,
                        committed = true
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isCommitting = false,
                        error = "Failed to commit: ${result.message}"
                    )
                }
            }
        }
    }

    fun regenerate() {
        detectAndGenerate()
    }
}
