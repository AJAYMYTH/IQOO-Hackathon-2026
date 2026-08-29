package com.apexos.repoguardian.ui.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexos.repoguardian.core.logging.AppLogger
import com.apexos.repoguardian.data.github.ApiResult
import com.apexos.repoguardian.data.github.GitHubRepository
import com.apexos.repoguardian.data.github.models.CommitDiffResponse
import com.apexos.repoguardian.data.github.models.PullRequest
import com.apexos.repoguardian.data.llm.LlamaService
import com.apexos.repoguardian.data.llm.ReviewResult
import com.apexos.repoguardian.data.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val owner: String = "",
    val repo: String = "",
    val sha: String = "",
    val commitDiff: CommitDiffResponse? = null,
    val reviewResult: ReviewResult? = null,
    val isLoadingDiff: Boolean = false,
    val isAnalyzing: Boolean = false,
    val isCreatingPr: Boolean = false,
    val createdPr: PullRequest? = null,
    val error: String? = null
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gitHubRepository: GitHubRepository,
    private val llamaService: LlamaService,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    companion object {
        private const val TAG = "ReviewViewModel"
    }

    private val owner: String = savedStateHandle["owner"] ?: ""
    private val repo: String = savedStateHandle["repo"] ?: ""
    private val sha: String = savedStateHandle["sha"] ?: ""

    private val _uiState = MutableStateFlow(ReviewUiState(
        owner = owner,
        repo = repo,
        sha = sha
    ))
    val uiState: StateFlow<ReviewUiState> = _uiState

    init {
        loadAndReview()
    }

    private fun loadAndReview() {
        viewModelScope.launch {
            // 1. Load diff
            _uiState.value = _uiState.value.copy(isLoadingDiff = true, error = null)
            AppLogger.i(TAG, "Loading commit diff from GitHub REST API for sha=$sha ($owner/$repo)")
            when (val result = gitHubRepository.getCommitDiff(owner, repo, sha)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        commitDiff = result.data,
                        isLoadingDiff = false
                    )
                    AppLogger.i(TAG, "Commit diff loaded (${result.data.files?.size ?: 0} files), starting AI analysis")
                    // 2. Run LLM analysis
                    analyzeWithLlm(result.data)
                }
                is ApiResult.Error -> {
                    AppLogger.e(TAG, "Failed to load diff from GitHub: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoadingDiff = false,
                        error = "Failed to load diff: ${result.message}"
                    )
                }
            }
        }
    }

    private fun analyzeWithLlm(diff: CommitDiffResponse) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)
            try {
                val diffText = diff.files?.joinToString("\n\n") { file ->
                    "--- a/${file.filename}\n+++ b/${file.filename}\n${file.patch ?: "(file changes without text patch)"}"
                } ?: "No files changed"

                val customRules = preferencesManager.getCustomRules()
                val repoContext = "Repository: $owner/$repo | Target Commit SHA: $sha | Author: ${diff.commit.author?.name ?: "Unknown"}"
                AppLogger.i(TAG, "Running AI review on commit diff (${diffText.length} chars)")
                val result = llamaService.reviewDiff(diffText, customRules, repoContext)

                _uiState.value = _uiState.value.copy(
                    reviewResult = result,
                    isAnalyzing = false
                )
                AppLogger.i(TAG, "AI Code Review finished: ${result.summary.take(80)}")
            } catch (e: Exception) {
                AppLogger.e(TAG, "AI Code Review analysis failed", e)
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    error = "Analysis failed: ${e.localizedMessage ?: e.message}"
                )
            }
        }
    }



    fun openPullRequest() {
        viewModelScope.launch {
            val review = _uiState.value.reviewResult ?: return@launch
            val diff = _uiState.value.commitDiff ?: return@launch
            val firstFile = diff.files?.firstOrNull() ?: return@launch

            _uiState.value = _uiState.value.copy(isCreatingPr = true, error = null)

            val prTitle = "fix: ${review.summary.take(60)}"
            val prBody = buildString {
                appendLine("## Repo Guardian - Automated Fix")
                appendLine()
                appendLine("**Analyzed commit:** ${sha.take(7)}")
                appendLine()
                appendLine("### Issues Found")
                review.issues.forEach { issue ->
                    appendLine("- **[${issue.severity.uppercase()}]** ${issue.description}")
                    issue.fix?.let { appendLine("  - Fix: $it") }
                }
                appendLine()
                appendLine("---")
                appendLine("*Generated by Repo Guardian (On-Device AI)*")
            }

            when (val result = gitHubRepository.createFixPr(
                owner = owner,
                repo = repo,
                baseSha = sha,
                filePath = firstFile.filename,
                fixedContent = review.fixedCode ?: "",
                commitMessage = "fix: ${review.issues.firstOrNull()?.description?.take(50) ?: "code improvement"}",
                prTitle = prTitle,
                prBody = prBody
            )) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isCreatingPr = false,
                        createdPr = result.data
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isCreatingPr = false,
                        error = "Failed to create PR: ${result.message}"
                    )
                }
            }
        }
    }
}
