package com.apexos.repoguardian.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexos.repoguardian.data.github.ApiResult
import com.apexos.repoguardian.data.github.GitHubRepository
import com.apexos.repoguardian.data.github.models.Commit
import com.apexos.repoguardian.data.github.models.Repo
import com.apexos.repoguardian.data.preferences.PreferencesManager
import com.apexos.repoguardian.data.voice.VoiceService
import com.apexos.repoguardian.data.voice.VoiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val repoOwner: String = "",
    val repoName: String = "",
    val availableRepos: List<Repo> = emptyList(),
    val commits: List<Commit> = emptyList(),
    val isLoading: Boolean = false,
    val isDropdownOpen: Boolean = false,
    val isGuideOpen: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val gitHubRepository: GitHubRepository,
    private val preferencesManager: PreferencesManager,
    val voiceService: VoiceService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    val voiceState: StateFlow<VoiceState> = voiceService.state

    init {
        checkGuideStatus()
        loadDashboard()
        loadAvailableRepos()
    }

    private fun checkGuideStatus() {
        viewModelScope.launch {
            val dismissed = preferencesManager.isGuideDismissed()
            if (!dismissed) {
                _uiState.value = _uiState.value.copy(isGuideOpen = true)
            }
        }
    }

    fun openGuide() {
        _uiState.value = _uiState.value.copy(isGuideOpen = true)
    }

    fun dismissGuide(dontShowAgain: Boolean) {
        viewModelScope.launch {
            if (dontShowAgain) {
                preferencesManager.setGuideDismissed(true)
            }
            _uiState.value = _uiState.value.copy(isGuideOpen = false)
        }
    }

    fun loadAvailableRepos() {
        viewModelScope.launch {
            when (val result = gitHubRepository.listRepos()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(availableRepos = result.data)
                }
                else -> {}
            }
        }
    }

    fun loadDashboard() {
        viewModelScope.launch {
            val repo = preferencesManager.getSelectedRepo()
            if (repo == null) {
                _uiState.value = _uiState.value.copy(error = "No repository selected")
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                repoOwner = repo.first,
                repoName = repo.second,
                isLoading = true,
                error = null
            )

            when (val result = gitHubRepository.listCommits(repo.first, repo.second)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        commits = result.data,
                        isLoading = false
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
            }
        }
    }

    fun setDropdownOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(isDropdownOpen = open)
    }

    fun switchRepo(repo: Repo) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                repoOwner = repo.owner.login,
                repoName = repo.name,
                isDropdownOpen = false,
                isLoading = true,
                commits = emptyList(),
                error = null
            )
            preferencesManager.saveSelectedRepo(repo.owner.login, repo.name)

            when (val result = gitHubRepository.listCommits(repo.owner.login, repo.name)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        commits = result.data,
                        isLoading = false
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
            }
        }
    }

    fun startVoiceTrigger() {
        voiceService.startListening()
    }

    fun stopVoiceTrigger() {
        voiceService.stop()
    }

    fun resetVoiceState() {
        voiceService.resetState()
    }
}
