package com.apexos.repoguardian.ui.modelbrowser

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexos.repoguardian.data.huggingface.DownloadProgress
import com.apexos.repoguardian.data.huggingface.FEATURED_MODELS
import com.apexos.repoguardian.data.huggingface.FeaturedModel
import com.apexos.repoguardian.data.huggingface.HfModelFile
import com.apexos.repoguardian.data.huggingface.HfModelSearchResult
import com.apexos.repoguardian.data.huggingface.ModelDownloadManager
import com.apexos.repoguardian.data.llm.LlamaService
import com.apexos.repoguardian.data.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ModelBrowserUiState(
    val selectedTab: Int = 0, // 0: Featured, 1: Hugging Face Search, 2: Downloaded
    val featuredModels: List<FeaturedModel> = FEATURED_MODELS,
    val searchQuery: String = "coder gguf",
    val searchResults: List<HfModelSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val selectedModel: HfModelSearchResult? = null,
    val modelFiles: List<HfModelFile> = emptyList(),
    val isLoadingFiles: Boolean = false,
    val downloadedModels: List<File> = emptyList(),
    val activeModelPath: String? = null,
    val downloadingFilename: String? = null,
    val downloadProgress: DownloadProgress? = null,
    val isLoadingModel: Boolean = false,
    val successMessage: String? = null,
    val error: String? = null
)

@HiltViewModel
class ModelBrowserViewModel @Inject constructor(
    private val modelDownloadManager: ModelDownloadManager,
    private val preferencesManager: PreferencesManager,
    private val llamaService: LlamaService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelBrowserUiState())
    val uiState: StateFlow<ModelBrowserUiState> = _uiState

    init {
        refreshState()
        search()
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index, error = null, successMessage = null)
        refreshState()
    }

    fun refreshState() {
        viewModelScope.launch {
            val downloaded = modelDownloadManager.getDownloadedModels()
            val activePath = preferencesManager.getModelPath()
            _uiState.value = _uiState.value.copy(
                downloadedModels = downloaded,
                activeModelPath = activePath
            )
        }
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
                    error = "Search failed: ${e.localizedMessage ?: e.message}"
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
                    error = "Failed to list files: ${e.localizedMessage ?: e.message}"
                )
            }
        }
    }

    fun downloadFeaturedModel(featured: FeaturedModel) {
        downloadFile(featured.id, featured.filename)
    }

    fun downloadFile(modelId: String, filename: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                downloadingFilename = filename,
                downloadProgress = DownloadProgress(),
                error = null,
                successMessage = null
            )
            modelDownloadManager.downloadModel(modelId, filename).collect { progress ->
                _uiState.value = _uiState.value.copy(downloadProgress = progress)

                if (progress.isComplete) {
                    val downloadedFile = File(modelDownloadManager.getModelsDirectory(), filename)
                    loadAndActivateModel(downloadedFile)
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

    fun loadAndActivateModel(file: File) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingModel = true,
                downloadingFilename = null,
                downloadProgress = null
            )
            try {
                val path = file.absolutePath
                val backend = preferencesManager.getBackend()
                val gpuLayers = if (backend == "gpu" || backend == "npu") 33 else 0

                llamaService.unload()
                llamaService.loadModel(path, gpuLayers)
                preferencesManager.saveModelPath(path)

                _uiState.value = _uiState.value.copy(
                    isLoadingModel = false,
                    activeModelPath = path,
                    downloadedModels = modelDownloadManager.getDownloadedModels(),
                    successMessage = "✓ ${file.name} loaded successfully and set as active model!"
                )
                Log.d("ModelBrowserVM", "Loaded model: $path")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingModel = false,
                    error = "Failed to load model: ${e.localizedMessage ?: e.message}"
                )
            }
        }
    }

    fun deleteModel(file: File) {
        viewModelScope.launch {
            val wasActive = _uiState.value.activeModelPath == file.absolutePath
            if (wasActive) {
                llamaService.unload()
                preferencesManager.saveModelPath("")
            }
            modelDownloadManager.deleteModel(file.name)
            refreshState()
            _uiState.value = _uiState.value.copy(
                successMessage = "Model ${file.name} deleted"
            )
        }
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedModel = null,
            modelFiles = emptyList()
        )
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}
