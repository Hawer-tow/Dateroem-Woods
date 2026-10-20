package com.dateroem.woodsales.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.model.LoginRequest
import com.dateroem.woodsales.data.network.ApiService
import com.dateroem.woodsales.data.security.DeviceIdentity
import com.dateroem.woodsales.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import com.dateroem.woodsales.ui.common.friendlyError

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Error(val message: String) : LoginUiState
    // "allows only one user per account per mobile phone" - this phone is
    // new for the account and an admin must approve it first.
    data class AwaitingApproval(val message: String, val phoneName: String) : LoginUiState
    data object Success : LoginUiState
}

class AuthViewModel(
    private val api: ApiService,
    private val sessionManager: SessionManager,
    private val deviceIdentity: DeviceIdentity
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(userName: String, password: String) {
        if (userName.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("Enter both username and password")
            return
        }
        sessionManager.clearSignOutReason()
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                // The phone proves which install it is: a fresh one-time
                // challenge from the server, signed by this phone's own key.
                val challenge = api.deviceChallenge().challenge
                val proof = withContext(Dispatchers.Default) { deviceIdentity.proofFor(challenge) }
                val response = api.login(LoginRequest(userName.trim(), password, proof))
                sessionManager.save(response)
                _uiState.value = LoginUiState.Success
            } catch (e: Exception) {
                _uiState.value = describe(e)
            }
        }
    }

    private fun describe(e: Exception): LoginUiState {
        if (e is HttpException) {
            val body = e.response()?.errorBody()?.string().orEmpty()
            val detail = Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            if (detail != null) {
                val text = detail.substringAfter(": ", detail)
                return when {
                    detail.startsWith("DEVICE_PENDING") -> LoginUiState.AwaitingApproval(text, DeviceIdentity.phoneName())
                    else -> LoginUiState.Error(text)
                }
            }
        }
        return LoginUiState.Error(friendlyError(e, "Sign-in failed - check your connection"))
    }

    fun logout() {
        viewModelScope.launch { sessionManager.clear() }
    }

    class Factory(
        private val api: ApiService,
        private val sessionManager: SessionManager,
        private val deviceIdentity: DeviceIdentity
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(api, sessionManager, deviceIdentity) as T
        }
    }
}
