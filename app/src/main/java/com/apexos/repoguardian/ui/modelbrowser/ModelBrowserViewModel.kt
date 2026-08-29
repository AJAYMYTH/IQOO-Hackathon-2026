package com.apexos.repoguardian.ui.modelbrowser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexos.repoguardian.data.huggingface.DownloadProgress
import com.apexos.repoguardian.data.huggingface.HfModelFile
import com.apexos.repoguardian.data.huggingface.HfModelSearchResult
import com.apexos.repoguardian.data.huggingface.ModelDownloadManager
import com.apexos.repoguardian.data.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelBrowserUiState(
    val searchQuery: String = "coder gguf",
    val searchResults: List<HfModelSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val selectedModel: HfModelSearchResult? = null,
    val modelFiles: List<HfModelFile> = emptyList(),
    val isLoadingFiles: Boolean = false,
    val downloadProgress: DownloadProgress? = null,
    val downloadingFilename: String? = null,
    val error: String? = null
)

@HiltViewModel
class ModelBrowserViewModel @Inject constructor(
    private val modelDownloadManager: ModelDownloadManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelBrowserUiState())
    val uiState: StateFlow<ModelBrowserUiState> = _uiState

    init {
        search()
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun search() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, error = null)
            try {
                val results = modelDownloadManager.searchModels(_uiState.value.searchQuery)
                _uiState.value = _uiState.value.copy(
                    searchResults = results,
                    isSearching = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = "Search failed: ${e.message}"
                )
            }
        }
    }

    fun selectModel(model: HfModelSearchResult) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedModel = model,
                isLoadingFiles = true,
                modelFiles = emptyList()
            )
            try {
                val files = modelDownloadManager.listGgufFiles(model.id)
                _uiState.value = _uiState.value.copy(
                    modelFiles = files,
                    isLoadingFiles = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingFiles = false,
                    error = "Failed to list files: ${e.message}"
                )
            }
        }
    }

    fun downloadFile(modelId: String, file: HfModelFile) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                downloadingFilename = file.filename,
                downloadProgress = DownloadProgress()
            )
            modelDownloadManager.downloadModel(modelId, file.filename).collect { progress ->
                _uiState.value = _uiState.value.copy(downloadProgress = progress)

                if (progress.isComplete) {
                    // Auto-set the model path
                    val modelFile = java.io.File(
                        modelDownloadManager.getModelsDirectory(),
                        file.filename
                    )
                    preferencesManager.saveModelPath(modelFile.absolutePath)
                    _uiState.value = _uiState.value.copy(
                        downloadingFilename = null,
                        downloadProgress = null
                    )
                }

                if (progress.error != null) {
                    _uiState.value = _uiState.value.copy(
                        downloadingFilename = null,
                        downloadProgress = null,
                        error = progress.error
                    )
                }
            }
        }
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedModel = null,
            modelFiles = emptyList()
        )
    }
}
