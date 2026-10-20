package com.dateroem.woodsales.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.model.CashSplitRequest
import com.dateroem.woodsales.data.network.ApiService
import com.dateroem.woodsales.data.network.JsonMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.dateroem.woodsales.ui.common.friendlyError

sealed interface CapitalUiState {
    data object Loading : CapitalUiState
    data class Loaded(val trip: JsonMap, val capital: List<JsonMap>) : CapitalUiState
    data class Error(val message: String) : CapitalUiState
}

// "when you click capital what we have in the trip area should have been
// here" - the trip's hard/soft cash split (previously edited straight on
// the Trip Detail screen - "i should not have a financial filling area"
// there) lives here instead, alongside the trip's recorded CAPITAL rows.
class CapitalViewModel(private val api: ApiService, private val tripId: Int) : ViewModel() {

    private val _uiState = MutableStateFlow<CapitalUiState>(CapitalUiState.Loading)
    val uiState: StateFlow<CapitalUiState> = _uiState.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _recalculating = MutableStateFlow(false)
    val recalculating: StateFlow<Boolean> = _recalculating.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = CapitalUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                CapitalUiState.Loaded(api.getTrip(tripId), api.tripCapital(tripId))
            } catch (e: Exception) {
                CapitalUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    fun saveCashSplit(hardCash: Double, softCash: Double) {
        _saving.value = true
        viewModelScope.launch {
            try {
                api.setCashSplit(tripId, CashSplitRequest(hardCash, softCash))
                refresh()
            } finally {
                _saving.value = false
            }
        }
    }

    fun recalculateTotalPayment() {
        _recalculating.value = true
        viewModelScope.launch {
            try {
                api.recalculateTotalPayment(tripId)
                refresh()
            } finally {
                _recalculating.value = false
            }
        }
    }

    class Factory(private val api: ApiService, private val tripId: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CapitalViewModel(api, tripId) as T
    }
}
