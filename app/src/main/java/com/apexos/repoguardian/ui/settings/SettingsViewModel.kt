package com.apexos.repoguardian.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexos.repoguardian.data.huggingface.ModelDownloadManager
import com.apexos.repoguardian.data.llm.LlamaService
import com.apexos.repoguardian.data.llm.ModelState
import com.apexos.repoguardian.data.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val modelPath: String = "",
    val selectedBackend: String = "cpu",
    val customRules: String = "",
    val downloadedModels: List<File> = emptyList(),
    val modelState: ModelState = ModelState.NotLoaded,
    val isSaving: Boolean = false,
    val savedMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val llamaService: LlamaService,
    private val modelDownloadManager: ModelDownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val modelPath = preferencesManager.getModelPath() ?: ""
            val backend = preferencesManager.getBackend()
            val rules = preferencesManager.getCustomRules()
            val downloadedModels = modelDownloadManager.getDownloadedModels()

            _uiState.value = SettingsUiState(
                modelPath = modelPath,
                selectedBackend = backend,
                customRules = rules,
                downloadedModels = downloadedModels,
                modelState = llamaService.modelState.value
            )
        }

        // Observe model state
        viewModelScope.launch {
            llamaService.modelState.collect { state ->
                _uiState.value = _uiState.value.copy(modelState = state)
            }
        }
    }

    fun updateModelPath(path: String) {
        _uiState.value = _uiState.value.copy(modelPath = path)
    }

    fun updateBackend(backend: String) {
        _uiState.value = _uiState.value.copy(selectedBackend = backend)
    }

    fun updateCustomRules(rules: String) {
        _uiState.value = _uiState.value.copy(customRules = rules)
    }

    fun selectDownloadedModel(file: File) {
        _uiState.value = _uiState.value.copy(modelPath = file.absolutePath)
    }

    fun saveSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            preferencesManager.saveModelPath(_uiState.value.modelPath)
            preferencesManager.saveBackend(_uiState.value.selectedBackend)
            preferencesManager.saveCustomRules(_uiState.value.customRules)

            // Reload model with new settings
            if (_uiState.value.modelPath.isNotBlank()) {
                val gpuLayers = when (_uiState.value.selectedBackend) {
                    "gpu", "npu" -> 99
                    else -> 0
                }
                llamaService.unload()
                llamaService.loadModel(_uiState.value.modelPath, gpuLayers)
            }

            _uiState.value = _uiState.value.copy(
                isSaving = false,
                savedMessage = "Settings saved!"
            )
        }
    }

    fun clearSavedMessage() {
        _uiState.value = _uiState.value.copy(savedMessage = null)
    }
}
