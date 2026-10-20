package com.dateroem.woodsales.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.network.ApiService
import com.dateroem.woodsales.data.network.JsonMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.dateroem.woodsales.ui.common.friendlyError

sealed interface TripsUiState {
    data object Loading : TripsUiState
    data class Loaded(val trips: List<JsonMap>) : TripsUiState
    data class Error(val message: String) : TripsUiState
}

// "this is the section where i want one to create a trip so that then the
// rest of the sections are active since almost all are connected to the
// trip factor ... im not able to see the rest of the sections until this
// section trips is made and a trip is made."
class TripsViewModel(private val api: ApiService) : ViewModel() {

    private val _uiState = MutableStateFlow<TripsUiState>(TripsUiState.Loading)
    val uiState: StateFlow<TripsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = TripsUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                TripsUiState.Loaded(api.listTrips())
            } catch (e: Exception) {
                TripsUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    class Factory(private val api: ApiService) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TripsViewModel(api) as T
    }
}
