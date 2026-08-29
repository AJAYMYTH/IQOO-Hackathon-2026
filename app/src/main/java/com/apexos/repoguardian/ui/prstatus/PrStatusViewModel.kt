package com.apexos.repoguardian.ui.prstatus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexos.repoguardian.data.github.ApiResult
import com.apexos.repoguardian.data.github.GitHubRepository
import com.apexos.repoguardian.data.github.models.CheckRun
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrStatusUiState(
    val owner: String = "",
    val repo: String = "",
    val prNumber: Int = 0,
    val checkRuns: List<CheckRun> = emptyList(),
    val isLoading: Boolean = false,
    val isPolling: Boolean = false,
    val noRunnerMessage: String? = null,
    val error: String? = null
)

@HiltViewModel
class PrStatusViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gitHubRepository: GitHubRepository
) : ViewModel() {

    private val owner: String = savedStateHandle["owner"] ?: ""
    private val repo: String = savedStateHandle["repo"] ?: ""
    private val prNumber: Int = savedStateHandle["prNumber"] ?: 0

    private val _uiState = MutableStateFlow(PrStatusUiState(
        owner = owner,
        repo = repo,
        prNumber = prNumber
    ))
    val uiState: StateFlow<PrStatusUiState> = _uiState

    private var polling = true

    init {
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            while (polling) {
                when (val result = gitHubRepository.getCheckRuns(owner, repo, "main")) {
                    is ApiResult.Success -> {
                        val runs = result.data.checkRuns
                        if (runs.isEmpty()) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isPolling = true,
                                noRunnerMessage = "No check runs found. This could mean:\n" +
                                    "• The self-hosted runner is not active (Red Light phase)\n" +
                                    "• No CI/CD workflow is configured for this repo\n\n" +
                                    "The status will auto-update when a runner becomes available."
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                checkRuns = runs,
                                isLoading = false,
                                isPolling = true,
                                noRunnerMessage = null
                            )

                            // Stop polling if all checks are complete
                            val allComplete = runs.all { it.status == "completed" }
                            if (allComplete) {
                                polling = false
                                _uiState.value = _uiState.value.copy(isPolling = false)
                            }
                        }
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }

                if (polling) delay(10_000) // Poll every 10 seconds
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        polling = false
    }
}
