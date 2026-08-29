package com.apexos.repoguardian.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexos.repoguardian.data.github.AuthState
import com.apexos.repoguardian.data.github.GitHubAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: GitHubAuthManager
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    fun startAuth() {
        viewModelScope.launch {
            authManager.startDeviceFlow().collect { state ->
                _authState.value = state
            }
        }
    }
}
