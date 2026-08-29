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
    data class WaitingForUser(
        val response: DeviceCodeResponse,
        val transitionCountdown: Int = 5
    ) : AuthState()
    data class Verifying(
        val response: DeviceCodeResponse,
        val remainingSeconds: Int = 30
    ) : AuthState()
    data class Success(val token: String) : AuthState()
    data class Timeout(
        val message: String = "Verification timed out. Please try again.",
        val expiredCode: String? = null
    ) : AuthState()
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

    suspend fun requestDeviceCode(): DeviceCodeResponse {
        val clientId = BuildConfig.GITHUB_CLIENT_ID
        if (clientId.isBlank()) {
            throw IllegalStateException("GitHub Client ID not configured. Add GITHUB_CLIENT_ID to local.properties")
        }
        return authApi.requestDeviceCode(
            DeviceCodeRequest(clientId = clientId)
        )
    }

    suspend fun pollToken(deviceCode: String): String? {
        val clientId = BuildConfig.GITHUB_CLIENT_ID
        val tokenResponse = authApi.pollAccessToken(
            AccessTokenRequest(
                clientId = clientId,
                deviceCode = deviceCode
            )
        )

        when {
            tokenResponse.accessToken != null -> {
                preferencesManager.saveGitHubToken(tokenResponse.accessToken)
                return tokenResponse.accessToken
            }
            tokenResponse.error == "authorization_pending" -> {
                return null
            }
            tokenResponse.error == "slow_down" -> {
                return null
            }
            tokenResponse.error == "expired_token" -> {
                throw IllegalStateException("Device code expired. Please try again.")
            }
            tokenResponse.error == "access_denied" -> {
                throw IllegalStateException("Access denied by user.")
            }
            else -> {
                if (!tokenResponse.error.isNullOrBlank()) {
                    throw IllegalStateException(tokenResponse.errorDescription ?: tokenResponse.error)
                }
                return null
            }
        }
    }

    suspend fun logout() {
        preferencesManager.clearGitHubToken()
    }
}
