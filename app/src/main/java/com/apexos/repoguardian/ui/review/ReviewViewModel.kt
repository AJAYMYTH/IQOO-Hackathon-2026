package com.apexos.repoguardian.ui.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexos.repoguardian.core.logging.AppLogger
import com.apexos.repoguardian.data.github.ApiResult
import com.apexos.repoguardian.data.github.GitHubRepository
import com.apexos.repoguardian.data.github.models.CommitDiffResponse
import com.apexos.repoguardian.data.github.models.PullRequest
import com.apexos.repoguardian.data.llm.CodeIssue
import com.apexos.repoguardian.data.llm.LlamaService
import com.apexos.repoguardian.data.llm.ReviewResult
import com.apexos.repoguardian.data.llm.Severity
import com.apexos.repoguardian.data.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SeverityFilter(val label: String) {
    ALL("All"),
    CRITICAL("Critical"),
    WARNING("Warning"),
    INFO("Info")
}

data class ReviewUiState(
    val owner: String = "",
    val repo: String = "",
    val sha: String = "",
    val commitDiff: CommitDiffResponse? = null,
    val reviewResult: ReviewResult? = null,
    val selectedSeverityFilter: SeverityFilter = SeverityFilter.ALL,
    val isLoadingDiff: Boolean = false,
    val isAnalyzing: Boolean = false,
    val isCreatingPr: Boolean = false,
    val createdPr: PullRequest? = null,
    val error: String? = null
) {
    val filteredIssues: List<CodeIssue> get() {
        val allSorted = reviewResult?.sortedIssues ?: emptyList()
        return when (selectedSeverityFilter) {
            SeverityFilter.ALL -> allSorted
            SeverityFilter.CRITICAL -> allSorted.filter { it.severityEnum == Severity.CRITICAL }
            SeverityFilter.WARNING -> allSorted.filter { it.severityEnum == Severity.WARNING }
            SeverityFilter.INFO -> allSorted.filter { it.severityEnum == Severity.INFO || it.severityEnum == Severity.UNKNOWN }
        }
    }
}

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

    fun setSeverityFilter(filter: SeverityFilter) {
        _uiState.value = _uiState.value.copy(selectedSeverityFilter = filter)
    }

    fun retryAnalysis() {
        val diff = _uiState.value.commitDiff
        if (diff != null) {
            analyzeWithLlm(diff)
        } else {
            loadAndReview()
        }
    }

    private fun loadAndReview() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingDiff = true, error = null)
            AppLogger.i(TAG, "Loading commit diff from GitHub REST API for sha=$sha ($owner/$repo)")
            when (val result = gitHubRepository.getCommitDiff(owner, repo, sha)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        commitDiff = result.data,
                        isLoadingDiff = false
                    )
                    AppLogger.i(TAG, "Commit diff loaded (${result.data.files?.size ?: 0} files), starting AI analysis")
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
            _uiState.value = _uiState.value.copy(isAnalyzing = true, error = null)
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
                appendLine("## Repo Guardian Review Summary")
                appendLine()
                appendLine("- **Critical:** ${review.criticalCount}")
                appendLine("- **Warning:** ${review.warningCount}")
                appendLine("- **Info:** ${review.infoCount}")
                appendLine()

                val criticals = review.issues.filter { it.severityEnum == Severity.CRITICAL }
                if (criticals.isNotEmpty()) {
                    appendLine("### 🚨 Critical Issues")
                    criticals.forEach { issue ->
                        val loc = if (issue.file != null) " in `${issue.file}${if (issue.line != null) ":${issue.line}" else ""}`" else ""
                        appendLine("- **${issue.displayTitle}**$loc")
                        appendLine("  ${issue.description}")
                        issue.displayFix?.let { appendLine("  - *Remediation:* $it") }
                    }
                    appendLine()
                }

                val warnings = review.issues.filter { it.severityEnum == Severity.WARNING }
                if (warnings.isNotEmpty()) {
                    appendLine("### ⚠️ Warning Issues")
                    warnings.forEach { issue ->
                        val loc = if (issue.file != null) " in `${issue.file}${if (issue.line != null) ":${issue.line}" else ""}`" else ""
                        appendLine("- **${issue.displayTitle}**$loc")
                        appendLine("  ${issue.description}")
                        issue.displayFix?.let { appendLine("  - *Remediation:* $it") }
                    }
                    appendLine()
                }

                val infos = review.issues.filter { it.severityEnum == Severity.INFO || it.severityEnum == Severity.UNKNOWN }
                if (infos.isNotEmpty()) {
                    appendLine("### ℹ️ Informational & Style")
                    infos.forEach { issue ->
                        val loc = if (issue.file != null) " in `${issue.file}${if (issue.line != null) ":${issue.line}" else ""}`" else ""
                        appendLine("- **${issue.displayTitle}**$loc")
                        issue.displayFix?.let { appendLine("  - *Suggestion:* $it") }
                    }
                    appendLine()
                }

                appendLine("---")
                appendLine("*Generated locally and privately on-device by Repo Guardian AI.*")
            }

            val repoResult = gitHubRepository.getRepo(owner, repo)
            val defaultBranch = (repoResult as? ApiResult.Success)?.data?.defaultBranch ?: "main"

            when (val result = gitHubRepository.createFixPr(
                owner = owner,
                repo = repo,
                baseSha = sha,
                filePath = firstFile.filename,
                fixedContent = review.fixedCode ?: "",
                commitMessage = "fix: ${review.issues.firstOrNull()?.displayTitle?.take(50) ?: "code improvement"}",
                prTitle = prTitle,
                prBody = prBody,
                baseBranch = defaultBranch
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
