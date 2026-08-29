package com.apexos.repoguardian.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexos.repoguardian.data.github.AuthState
import com.apexos.repoguardian.data.github.GitHubAuthManager
import com.apexos.repoguardian.data.github.models.DeviceCodeResponse
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

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private var flowJob: Job? = null

    fun startAuth() {
        flowJob?.cancel()
        flowJob = viewModelScope.launch {
            _authState.value = AuthState.Idle
            try {
                val deviceCodeResponse = authManager.requestDeviceCode()

                // Phase 1: 5-Second Transition Countdown on Auth Code Screen
                for (countdown in 5 downTo 1) {
                    if (!isActive) return@launch
                    _authState.value = AuthState.WaitingForUser(
                        response = deviceCodeResponse,
                        transitionCountdown = countdown
                    )
                    delay(1000L)
                }

                // Phase 2: Automatically transition to 30-Second Verification Screen
                proceedToVerification(deviceCodeResponse)

            } catch (e: Exception) {
                if (isActive) {
                    _authState.value = AuthState.Error(e.message ?: "Failed to start GitHub authorization")
                }
            }
        }
    }

    fun proceedToVerificationEarly() {
        val currentState = _authState.value
        if (currentState is AuthState.WaitingForUser) {
            flowJob?.cancel()
            flowJob = viewModelScope.launch {
                proceedToVerification(currentState.response)
            }
        }
    }

    private suspend fun proceedToVerification(deviceCodeResponse: DeviceCodeResponse) {
        var remainingSeconds = 30
        _authState.value = AuthState.Verifying(
            response = deviceCodeResponse,
            remainingSeconds = remainingSeconds
        )

        val intervalMs = (deviceCodeResponse.interval.coerceAtLeast(5) * 1000).toLong()
        var lastPollTime = 0L

        while (remainingSeconds > 0) {
            val now = System.currentTimeMillis()
            if (now - lastPollTime >= intervalMs) {
                lastPollTime = now
                try {
                    val token = authManager.pollToken(deviceCodeResponse.deviceCode)
                    if (token != null) {
                        _authState.value = AuthState.Success(token)
                        return
                    }
                } catch (e: Exception) {
                    _authState.value = AuthState.Timeout(
                        message = e.message ?: "Verification timed out. Please try again.",
                        expiredCode = deviceCodeResponse.userCode
                    )
                    return
                }
            }

            delay(1000L)
            remainingSeconds -= 1
            if (remainingSeconds > 0) {
                _authState.value = AuthState.Verifying(
                    response = deviceCodeResponse,
                    remainingSeconds = remainingSeconds
                )
            }
        }

        // 30 seconds expired without authorization
        _authState.value = AuthState.Timeout(
            message = "Verification timed out. Please try again.",
            expiredCode = deviceCodeResponse.userCode
        )
    }

    override fun onCleared() {
        super.onCleared()
        flowJob?.cancel()
    }
}
