package com.dateroem.woodsales.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.model.AssignTransactionRequest
import com.dateroem.woodsales.data.network.ApiService
import com.dateroem.woodsales.data.network.JsonMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import com.dateroem.woodsales.ui.common.friendlyError

sealed interface AssignDetailUiState {
    data object Loading : AssignDetailUiState
    data class Loaded(val candidates: List<JsonMap>) : AssignDetailUiState
    data class Error(val message: String) : AssignDetailUiState
}

// The admin assign screen's suggestion list - business rules: "the system
// checks for a record of 20 ksh in the holders and provides a list if they
// are many and also checks when they were recorded and the one that
// almost matches is assigned that". Ranked by the backend, closest match
// first; the admin still taps to confirm rather than it auto-assigning.
class AssignDetailViewModel(
    private val api: ApiService,
    private val financialTransactionId: Int,
    private val actorUserId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow<AssignDetailUiState>(AssignDetailUiState.Loading)
    val uiState: StateFlow<AssignDetailUiState> = _uiState.asStateFlow()

    private val _assigning = MutableStateFlow(false)
    val assigning: StateFlow<Boolean> = _assigning.asStateFlow()

    private val _assignError = MutableStateFlow<String?>(null)
    val assignError: StateFlow<String?> = _assignError.asStateFlow()

    private val _assigned = MutableStateFlow(false)
    val assigned: StateFlow<Boolean> = _assigned.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = AssignDetailUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                AssignDetailUiState.Loaded(api.transactionCandidates(financialTransactionId))
            } catch (e: Exception) {
                AssignDetailUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    fun assign(target: String, targetRowId: Int) {
        _assigning.value = true
        _assignError.value = null
        viewModelScope.launch {
            try {
                api.assignTransaction(financialTransactionId, AssignTransactionRequest(target, targetRowId), actorUserId)
                _assigned.value = true
            } catch (e: Exception) {
                _assignError.value = describeError(e)
            } finally {
                _assigning.value = false
            }
        }
    }

    private fun describeError(e: Exception): String {
        if (e is HttpException) {
            val body = e.response()?.errorBody()?.string()
            return friendlyError(e, "Something went wrong. Please try again.", body)
        }
        return friendlyError(e, "Couldn't assign")
    }

    class Factory(
        private val api: ApiService,
        private val financialTransactionId: Int,
        private val actorUserId: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AssignDetailViewModel(api, financialTransactionId, actorUserId) as T
    }
}
