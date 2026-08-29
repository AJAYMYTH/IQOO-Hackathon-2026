package com.apexos.repoguardian.ui.cicd

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexos.repoguardian.data.github.ApiResult
import com.apexos.repoguardian.data.github.GitHubRepository
import com.apexos.repoguardian.data.github.models.Repo
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
            try {
                // Get repo info to detect language
                val reposResult = gitHubRepository.listRepos()
                val language = when (reposResult) {
                    is ApiResult.Success -> {
                        reposResult.data.find { it.name == repo && it.owner.login == owner }?.language
                    }
                    else -> null
                }

                _uiState.value = _uiState.value.copy(detectedLanguage = language ?: "Unknown")

                // Generate YAML with LLM
                val yaml = llamaService.generateCiCdYaml(language, repo)
                _uiState.value = _uiState.value.copy(
                    generatedYaml = yaml,
                    isGenerating = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    error = "Generation failed: ${e.message}"
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
