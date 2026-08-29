package com.apexos.repoguardian.data.github

import com.apexos.repoguardian.BuildConfig
import com.apexos.repoguardian.data.github.models.AccessTokenRequest
import com.apexos.repoguardian.data.github.models.DeviceCodeRequest
import com.apexos.repoguardian.data.github.models.DeviceCodeResponse
import com.apexos.repoguardian.data.preferences.PreferencesManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthState {
    data object Idle : AuthState()
    data class WaitingForUser(val response: DeviceCodeResponse) : AuthState()
    data object Polling : AuthState()
    data class Success(val token: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

@Singleton
class GitHubAuthManager @Inject constructor(
    private val authApi: GitHubAuthApi,
    private val preferencesManager: PreferencesManager
) {
    suspend fun isAuthenticated(): Boolean {
        return preferencesManager.getGitHubToken() != null
    }

    suspend fun getToken(): String? {
        return preferencesManager.getGitHubToken()
    }

    fun startDeviceFlow(): Flow<AuthState> = flow {
        emit(AuthState.Idle)
        try {
            val clientId = BuildConfig.GITHUB_CLIENT_ID
            if (clientId.isBlank()) {
                emit(AuthState.Error("GitHub Client ID not configured. Add GITHUB_CLIENT_ID to local.properties"))
                return@flow
            }

            val deviceCodeResponse = authApi.requestDeviceCode(
                DeviceCodeRequest(clientId = clientId)
            )
            emit(AuthState.WaitingForUser(deviceCodeResponse))

            // Poll for token
            val intervalMs = (deviceCodeResponse.interval * 1000).toLong()
            val expiresAt = System.currentTimeMillis() + (deviceCodeResponse.expiresIn * 1000L)

            while (System.currentTimeMillis() < expiresAt) {
                delay(intervalMs)
                emit(AuthState.Polling)

                try {
                    val tokenResponse = authApi.pollAccessToken(
                        AccessTokenRequest(
                            clientId = clientId,
                            deviceCode = deviceCodeResponse.deviceCode
                        )
                    )

                    when {
                        tokenResponse.accessToken != null -> {
                            preferencesManager.saveGitHubToken(tokenResponse.accessToken)
                            emit(AuthState.Success(tokenResponse.accessToken))
                            return@flow
                        }
                        tokenResponse.error == "authorization_pending" -> {
                            // Continue polling
                        }
                        tokenResponse.error == "slow_down" -> {
                            delay(5000) // Extra delay
                        }
                        tokenResponse.error == "expired_token" -> {
                            emit(AuthState.Error("Device code expired. Please try again."))
                            return@flow
                        }
                        tokenResponse.error == "access_denied" -> {
                            emit(AuthState.Error("Access denied by user."))
                            return@flow
                        }
                        else -> {
                            emit(AuthState.Error(tokenResponse.errorDescription ?: "Unknown error"))
                            return@flow
                        }
                    }
                } catch (e: Exception) {
                    // Network error during polling, continue trying
                }
            }
            emit(AuthState.Error("Device code expired. Please try again."))
        } catch (e: Exception) {
            emit(AuthState.Error("Failed to start authentication: ${e.message}"))
        }
    }

    suspend fun logout() {
        preferencesManager.clearGitHubToken()
    }
}
