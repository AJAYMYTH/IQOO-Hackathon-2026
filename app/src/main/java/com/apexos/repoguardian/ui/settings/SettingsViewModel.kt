package com.apexos.repoguardian.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexos.repoguardian.data.github.ApiResult
import com.apexos.repoguardian.data.github.GitHubRepository
import com.apexos.repoguardian.data.github.models.GitHubUser
import com.apexos.repoguardian.data.huggingface.ModelDownloadManager
import com.apexos.repoguardian.data.llm.LlamaService
import com.apexos.repoguardian.data.llm.ModelState
import com.apexos.repoguardian.data.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import javax.inject.Inject

data class SettingsUiState(
    val user: GitHubUser? = null,
    val selectedRepoOwner: String = "",
    val selectedRepoName: String = "",
    val modelPath: String = "",
    val selectedBackend: String = "cpu",
    val customRules: String = "",
    val downloadedModels: List<File> = emptyList(),
    val modelState: ModelState = ModelState.NotLoaded,
    val appCacheSizeFormatted: String = "0 KB",
    val modelsSizeFormatted: String = "0 MB",
    val isSaving: Boolean = false,
    val savedMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val gitHubRepository: GitHubRepository,
    private val llamaService: LlamaService,
    private val modelDownloadManager: ModelDownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        loadSettings()
        loadUserProfile()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val modelPath = preferencesManager.getModelPath() ?: ""
            val backend = preferencesManager.getBackend()
            val rules = preferencesManager.getCustomRules()
            val repo = preferencesManager.getSelectedRepo()
            val downloadedModels = modelDownloadManager.getDownloadedModels()
            val (cacheFormatted, modelsFormatted) = calculateStorageSizes(downloadedModels)

            _uiState.value = _uiState.value.copy(
                modelPath = modelPath,
                selectedBackend = backend,
                customRules = rules,
                selectedRepoOwner = repo?.first ?: "",
                selectedRepoName = repo?.second ?: "",
                downloadedModels = downloadedModels,
                appCacheSizeFormatted = cacheFormatted,
                modelsSizeFormatted = modelsFormatted,
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

    private fun loadUserProfile() {
        viewModelScope.launch {
            when (val result = gitHubRepository.getUser()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(user = result.data)
                }
                is ApiResult.Error -> {
                    // Fallback to selected repo owner if direct user call fails
                    val repo = preferencesManager.getSelectedRepo()
                    if (repo != null && _uiState.value.user == null) {
                        _uiState.value = _uiState.value.copy(
                            user = GitHubUser(login = repo.first)
                        )
                    }
                }
            }
        }
    }

    private suspend fun calculateStorageSizes(models: List<File>): Pair<String, String> = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        val cacheSizeBytes = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val modelsSizeBytes = models.sumOf { it.length() }

        Pair(formatFileSize(cacheSizeBytes), formatFileSize(modelsSizeBytes))
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 KB"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val df = DecimalFormat("#,##0.#")
        return "${df.format(size / Math.pow(1024.0, index.toDouble()))} ${units[index]}"
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

    fun deleteDownloadedModel(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            if (file.exists()) {
                file.delete()
            }
            if (_uiState.value.modelPath == file.absolutePath) {
                _uiState.value = _uiState.value.copy(modelPath = "")
                preferencesManager.saveModelPath("")
                llamaService.unload()
            }
            val models = modelDownloadManager.getDownloadedModels()
            val (cacheFormatted, modelsFormatted) = calculateStorageSizes(models)
            _uiState.value = _uiState.value.copy(
                downloadedModels = models,
                appCacheSizeFormatted = cacheFormatted,
                modelsSizeFormatted = modelsFormatted,
                savedMessage = "Model deleted"
            )
        }
    }

    fun clearAppCache() {
        viewModelScope.launch(Dispatchers.IO) {
            context.cacheDir.deleteRecursively()
            context.cacheDir.mkdirs()
            val models = modelDownloadManager.getDownloadedModels()
            val (cacheFormatted, modelsFormatted) = calculateStorageSizes(models)
            _uiState.value = _uiState.value.copy(
                appCacheSizeFormatted = cacheFormatted,
                modelsSizeFormatted = modelsFormatted,
                savedMessage = "Application cache cleared successfully!"
            )
        }
    }

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            llamaService.unload()
            preferencesManager.clearGitHubToken()
            preferencesManager.clearSelectedRepo()
            onSuccess()
        }
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
                savedMessage = "Settings saved successfully!"
            )
        }
    }

    fun clearSavedMessage() {
        _uiState.value = _uiState.value.copy(savedMessage = null)
    }
}
