package com.apexos.repoguardian.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexos.repoguardian.data.llm.LlamaService
import com.apexos.repoguardian.data.llm.ModelState
import com.apexos.repoguardian.data.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SplashUiState(
    val statusMessage: String = "Initializing...",
    val isLoading: Boolean = true,
    val isAuthenticated: Boolean = false,
    val modelLoaded: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val llamaService: LlamaService,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch {
            try {
                // Check auth
                _uiState.value = _uiState.value.copy(statusMessage = "Checking authentication...")
                val token = preferencesManager.getGitHubToken()
                val isAuth = token != null

                // Load model
                _uiState.value = _uiState.value.copy(statusMessage = "Loading AI model...")
                val modelPath = preferencesManager.getModelPath()
                if (modelPath != null) {
                    val backend = preferencesManager.getBackend()
                    val gpuLayers = when (backend) {
                        "gpu" -> 99
                        "npu" -> 99
                        else -> 0
                    }
                    llamaService.loadModel(modelPath, gpuLayers)
                } else {
                    // No model configured, load in mock mode
                    llamaService.loadModel("/nonexistent", 0)
                }

                val modelLoaded = llamaService.isLoaded()

                _uiState.value = SplashUiState(
                    statusMessage = if (modelLoaded) "Ready!" else "Model not available",
                    isLoading = false,
                    isAuthenticated = isAuth,
                    modelLoaded = modelLoaded
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message,
                    statusMessage = "Error: ${e.message}"
                )
            }
        }
    }
}
