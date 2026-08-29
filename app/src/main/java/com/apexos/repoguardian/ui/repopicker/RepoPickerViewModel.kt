package com.apexos.repoguardian.ui.repopicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexos.repoguardian.data.github.ApiResult
import com.apexos.repoguardian.data.github.GitHubRepository
import com.apexos.repoguardian.data.github.models.Repo
import com.apexos.repoguardian.data.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RepoPickerUiState(
    val repos: List<Repo> = emptyList(),
    val filteredRepos: List<Repo> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class RepoPickerViewModel @Inject constructor(
    private val gitHubRepository: GitHubRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepoPickerUiState())
    val uiState: StateFlow<RepoPickerUiState> = _uiState

    init {
        loadRepos()
    }

    fun loadRepos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = gitHubRepository.listRepos()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        repos = result.data,
                        filteredRepos = result.data,
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

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredRepos = if (query.isBlank()) {
                _uiState.value.repos
            } else {
                _uiState.value.repos.filter {
                    it.name.contains(query, ignoreCase = true) ||
                    (it.description?.contains(query, ignoreCase = true) == true)
                }
            }
        )
    }

    fun selectRepo(repo: Repo) {
        viewModelScope.launch {
            preferencesManager.saveSelectedRepo(repo.owner.login, repo.name)
        }
    }
}
