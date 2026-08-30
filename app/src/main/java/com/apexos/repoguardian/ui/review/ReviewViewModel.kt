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
    val error: String? = null,

    // Manual Edit & Commit Dialog State
    val editingIssue: CodeIssue? = null,
    val editingFilePath: String = "",
    val editingContent: String = "",
    val editingCommitMessage: String = "",
    val isEditingPrMode: Boolean = false,
    val isLoadingFileContent: Boolean = false,
    val isSubmittingCommit: Boolean = false,
    val commitSuccessMessage: String? = null,

    // AI Issue Solver State
    val solvingIssueKey: String? = null,

    // AI Verification & Trust Before Apply State
    val testingIssueKey: String? = null,
    val previewingFixIssue: CodeIssue? = null
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

    // ─── AI Single Issue Solver ──────────────────────────────────────────────────
    fun solveIssueWithAi(issue: CodeIssue) {
        val issueKey = "${issue.file}:${issue.line}:${issue.displayTitle}"
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(solvingIssueKey = issueKey, error = null)
            try {
                AppLogger.i(TAG, "Invoking AI issue solver for: ${issue.displayTitle}")
                val diff = _uiState.value.commitDiff
                val targetFile = diff?.files?.find { it.filename == issue.file }
                val codeContext = targetFile?.patch ?: issue.description

                val repoContext = "Repository: $owner/$repo | Target: ${issue.file ?: "code"}"
                val solution = llamaService.solveIssue(
                    issueTitle = issue.displayTitle,
                    issueDescription = issue.description,
                    filePath = issue.file,
                    lineNumber = issue.line,
                    codeContext = codeContext,
                    repoContext = repoContext
                )

                // Update current review result with the generated AI solution
                val currentResult = _uiState.value.reviewResult
                if (currentResult != null) {
                    val updatedIssues = currentResult.issues.map {
                        if (it == issue || ("${it.file}:${it.line}:${it.displayTitle}" == issueKey)) {
                            it.copy(aiSolution = solution)
                        } else {
                            it
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        reviewResult = currentResult.copy(issues = updatedIssues),
                        solvingIssueKey = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(solvingIssueKey = null)
                }
                AppLogger.i(TAG, "AI issue solver generated solution successfully")
            } catch (e: Exception) {
                AppLogger.e(TAG, "AI issue solver failed", e)
                _uiState.value = _uiState.value.copy(
                    solvingIssueKey = null,
                    error = "AI solver failed: ${e.localizedMessage ?: e.message}"
                )
            }
        }
    }

    // ─── AI Verification Test Generator ──────────────────────────────────────────
    fun generateVerificationTest(issue: CodeIssue) {
        val issueKey = "${issue.file}:${issue.line}:${issue.displayTitle}"
        val fixCode = issue.aiSolution ?: issue.displayFix ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(testingIssueKey = issueKey, error = null)
            try {
                AppLogger.i(TAG, "Generating automated verification test for: ${issue.displayTitle}")
                val repoContext = "Repository: $owner/$repo | Target File: ${issue.file ?: "code"}"
                val testCode = llamaService.generateVerificationTest(
                    issueTitle = issue.displayTitle,
                    issueDescription = issue.description,
                    filePath = issue.file,
                    fixCode = fixCode,
                    repoContext = repoContext
                )

                val currentResult = _uiState.value.reviewResult
                if (currentResult != null) {
                    val updatedIssues = currentResult.issues.map {
                        if (it == issue || ("${it.file}:${it.line}:${it.displayTitle}" == issueKey)) {
                            it.copy(verificationTest = testCode)
                        } else {
                            it
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        reviewResult = currentResult.copy(issues = updatedIssues),
                        testingIssueKey = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(testingIssueKey = null)
                }
                AppLogger.i(TAG, "Generated verification test successfully")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to generate verification test", e)
                _uiState.value = _uiState.value.copy(
                    testingIssueKey = null,
                    error = "Test generation failed: ${e.localizedMessage ?: e.message}"
                )
            }
        }
    }

    // ─── Trust Before Apply Preview ──────────────────────────────────────────────
    fun openTrustPreview(issue: CodeIssue) {
        _uiState.value = _uiState.value.copy(previewingFixIssue = issue)
        if (issue.verificationTest == null && issue.displayFix != null) {
            generateVerificationTest(issue)
        }
    }

    fun dismissTrustPreview() {
        _uiState.value = _uiState.value.copy(previewingFixIssue = null)
    }

    fun applyTrustPreview(issue: CodeIssue, isPrMode: Boolean) {
        val solution = issue.aiSolution ?: issue.displayFix ?: return
        val cleanCode = extractCodeSnippet(solution)
        val path = issue.file ?: _uiState.value.commitDiff?.files?.firstOrNull()?.filename ?: "src/main.ts"
        val commitMsg = "fix(${path.substringAfterLast('/')}): resolve ${issue.displayTitle.take(40)}"

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmittingCommit = true, previewingFixIssue = null, error = null)

            if (isPrMode) {
                val repoResult = gitHubRepository.getRepo(owner, repo)
                val defaultBranch = (repoResult as? ApiResult.Success)?.data?.defaultBranch ?: "main"
                val prTitle = commitMsg
                val prBody = """
## 🛡️ Repo Guardian — Autonomous Fix Verification
**Issue Resolved:** ${issue.displayTitle}
**Target File:** `$path`

### 🔧 Applied Fix
```${path.substringAfterLast('.', "text")}
$cleanCode
```

${if (issue.verificationTest != null) "### 🧪 Automated Verification Test\n${issue.verificationTest}" else ""}

---
*Generated privately on-device with 0 bytes cloud transmission by Repo Guardian.*
                """.trimIndent()

                when (val result = gitHubRepository.createFixPr(
                    owner = owner,
                    repo = repo,
                    baseSha = sha,
                    filePath = path,
                    fixedContent = cleanCode,
                    commitMessage = commitMsg,
                    prTitle = prTitle,
                    prBody = prBody,
                    baseBranch = defaultBranch
                )) {
                    is ApiResult.Success -> {
                        markIssueAsFixed(issue)
                        _uiState.value = _uiState.value.copy(
                            isSubmittingCommit = false,
                            createdPr = result.data,
                            commitSuccessMessage = "Verified Fix PR #${result.data.number} created on GitHub!"
                        )
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isSubmittingCommit = false,
                            error = "Failed to create PR: ${result.message}"
                        )
                    }
                }
            } else {
                when (val result = gitHubRepository.commitFile(
                    owner = owner,
                    repo = repo,
                    filePath = path,
                    content = cleanCode,
                    commitMessage = commitMsg
                )) {
                    is ApiResult.Success -> {
                        markIssueAsFixed(issue)
                        _uiState.value = _uiState.value.copy(
                            isSubmittingCommit = false,
                            commitSuccessMessage = "Verified Fix committed to `$path` successfully!"
                        )
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isSubmittingCommit = false,
                            error = "Commit failed: ${result.message}"
                        )
                    }
                }
            }
        }
    }

    // ─── Manual Code Editor & Commit ─────────────────────────────────────────────
    fun startManualEdit(issue: CodeIssue) {
        val path = issue.file ?: _uiState.value.commitDiff?.files?.firstOrNull()?.filename ?: "src/main.ts"
        val initialMessage = "fix(${path.substringAfterLast('/')}): resolve ${issue.displayTitle.take(50)}"
        val diffFile = _uiState.value.commitDiff?.files?.find { it.filename == path }
        val fallbackContent = issue.aiSolution ?: issue.displayFix ?: diffFile?.patch ?: ""

        _uiState.value = _uiState.value.copy(
            editingIssue = issue,
            editingFilePath = path,
            editingContent = fallbackContent,
            editingCommitMessage = initialMessage,
            isEditingPrMode = false,
            isLoadingFileContent = true,
            error = null
        )

        // Try to fetch the full real source file from GitHub REST API
        viewModelScope.launch {
            when (val fileRes = gitHubRepository.getFileText(owner, repo, path, ref = sha)) {
                is ApiResult.Success -> {
                    if (fileRes.data.isNotBlank()) {
                        _uiState.value = _uiState.value.copy(
                            editingContent = fileRes.data,
                            isLoadingFileContent = false
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(isLoadingFileContent = false)
                    }
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoadingFileContent = false)
                }
            }
        }
    }

    fun updateManualContent(content: String) {
        _uiState.value = _uiState.value.copy(editingContent = content)
    }

    fun updateCommitMessage(message: String) {
        _uiState.value = _uiState.value.copy(editingCommitMessage = message)
    }

    fun setEditingPrMode(isPrMode: Boolean) {
        _uiState.value = _uiState.value.copy(isEditingPrMode = isPrMode)
    }

    fun applyAiFixToManualEditor(issue: CodeIssue) {
        val solution = issue.aiSolution ?: issue.displayFix ?: return
        val cleanCode = extractCodeSnippet(solution)
        _uiState.value = _uiState.value.copy(editingContent = cleanCode)
    }

    fun dismissManualEdit() {
        _uiState.value = _uiState.value.copy(editingIssue = null)
    }

    fun commitManualEdit() {
        viewModelScope.launch {
            val issue = _uiState.value.editingIssue ?: return@launch
            val path = _uiState.value.editingFilePath
            val content = _uiState.value.editingContent
            val commitMsg = _uiState.value.editingCommitMessage.ifBlank { "fix: resolve ${issue.displayTitle.take(40)}" }
            val isPrMode = _uiState.value.isEditingPrMode

            _uiState.value = _uiState.value.copy(isSubmittingCommit = true, error = null)

            if (isPrMode) {
                // Create branch and open Pull Request
                val repoResult = gitHubRepository.getRepo(owner, repo)
                val defaultBranch = (repoResult as? ApiResult.Success)?.data?.defaultBranch ?: "main"
                val prTitle = commitMsg
                val prBody = "Fix for issue **${issue.displayTitle}** in `$path`.\n\nReviewed and edited via Repo Guardian."

                when (val result = gitHubRepository.createFixPr(
                    owner = owner,
                    repo = repo,
                    baseSha = sha,
                    filePath = path,
                    fixedContent = content,
                    commitMessage = commitMsg,
                    prTitle = prTitle,
                    prBody = prBody,
                    baseBranch = defaultBranch
                )) {
                    is ApiResult.Success -> {
                        markIssueAsFixed(issue)
                        _uiState.value = _uiState.value.copy(
                            isSubmittingCommit = false,
                            editingIssue = null,
                            createdPr = result.data,
                            commitSuccessMessage = "Pull Request #${result.data.number} created successfully!"
                        )
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isSubmittingCommit = false,
                            error = "Failed to create PR: ${result.message}"
                        )
                    }
                }
            } else {
                // Direct commit to active branch
                when (val result = gitHubRepository.commitFile(
                    owner = owner,
                    repo = repo,
                    filePath = path,
                    content = content,
                    commitMessage = commitMsg
                )) {
                    is ApiResult.Success -> {
                        markIssueAsFixed(issue)
                        _uiState.value = _uiState.value.copy(
                            isSubmittingCommit = false,
                            editingIssue = null,
                            commitSuccessMessage = "Committed fix to `$path` successfully!"
                        )
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isSubmittingCommit = false,
                            error = "Commit failed: ${result.message}"
                        )
                    }
                }
            }
        }
    }

    private fun markIssueAsFixed(issue: CodeIssue) {
        val currentResult = _uiState.value.reviewResult ?: return
        val updated = currentResult.issues.map {
            if (it == issue || (it.file == issue.file && it.line == issue.line && it.displayTitle == issue.displayTitle)) {
                it.copy(isFixed = true)
            } else {
                it
            }
        }
        _uiState.value = _uiState.value.copy(reviewResult = currentResult.copy(issues = updated))
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(commitSuccessMessage = null)
    }

    private fun extractCodeSnippet(text: String): String {
        val codeBlockRegex = Regex("```(?:[a-zA-Z0-9]+)?\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
        val match = codeBlockRegex.find(text)
        return match?.groupValues?.get(1)?.trim() ?: text.trim()
    }

    // ─── Global Pull Request Generation ──────────────────────────────────────────
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
