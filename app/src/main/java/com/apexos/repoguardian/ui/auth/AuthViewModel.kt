package com.apexos.repoguardian.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexos.repoguardian.core.logging.AppLogger
import com.apexos.repoguardian.data.github.AuthState
import com.apexos.repoguardian.data.github.GitHubAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: GitHubAuthManager
) : ViewModel() {

    companion object {
        private const val TAG = "AuthViewModel"
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private var flowJob: Job? = null

    fun startAuth() {
        cancelAuth()
        flowJob = viewModelScope.launch {
            _authState.value = AuthState.Idle
            try {
                AppLogger.i(TAG, "Requesting GitHub device code...")
                val deviceCodeResponse = authManager.requestDeviceCode()
                AppLogger.i(TAG, "Device code received: ${deviceCodeResponse.userCode}. Starting polling without artificial timeouts.")

                _authState.value = AuthState.WaitingForUser(response = deviceCodeResponse)

                val intervalMs = (deviceCodeResponse.interval.coerceAtLeast(5) * 1000).toLong()
                val expiresInSeconds = deviceCodeResponse.expiresIn.coerceAtLeast(300)
                val expiryTimeMs = System.currentTimeMillis() + (expiresInSeconds * 1000L)

                while (isActive && System.currentTimeMillis() < expiryTimeMs) {
                    delay(intervalMs)
                    if (!isActive) break

                    try {
                        val token = authManager.pollToken(deviceCodeResponse.deviceCode)
                        if (token != null) {
                            AppLogger.i(TAG, "GitHub authorization successful! Token saved.")
                            _authState.value = AuthState.Success(token)
                            return@launch
                        }
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "GitHub polling exception: ${e.message}")
                        if (isActive) {
                            _authState.value = AuthState.Timeout(
                                message = e.message ?: "Authorization session expired. Please try again.",
                                expiredCode = deviceCodeResponse.userCode
                            )
                        }
                        return@launch
                    }
                }

                if (isActive) {
                    AppLogger.w(TAG, "GitHub device code expired after ${expiresInSeconds}s.")
                    _authState.value = AuthState.Timeout(
                        message = "GitHub authorization code expired. Please generate a new code.",
                        expiredCode = deviceCodeResponse.userCode
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to start GitHub authorization", e)
                if (isActive) {
                    _authState.value = AuthState.Error(e.message ?: "Failed to start GitHub authorization")
                }
            }
        }
    }

    fun cancelAuth() {
        if (flowJob?.isActive == true) {
            AppLogger.i(TAG, "Stopping GitHub authorization process (user exited or cancelled).")
            flowJob?.cancel()
            flowJob = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelAuth()
    }
}
