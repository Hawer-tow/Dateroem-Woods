package com.dateroem.woodsales.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.network.JsonMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface JsonListUiState {
    data object Loading : JsonListUiState
    data class Loaded(val rows: List<JsonMap>) : JsonListUiState
    data class Error(val message: String) : JsonListUiState
}

/**
 * Generic loader for the mobileServer sections whose response shape is
 * still a plain JsonMap list (Trips, Marketing campaigns, Bulk Orders) -
 * proves real communication for each section now, without committing to a
 * typed model before the exact fields these screens need are settled.
 */
class JsonListViewModel(private val fetch: suspend () -> List<JsonMap>) : ViewModel() {

    private val _uiState = MutableStateFlow<JsonListUiState>(JsonListUiState.Loading)
    val uiState: StateFlow<JsonListUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = JsonListUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                JsonListUiState.Loaded(fetch())
            } catch (e: Exception) {
                JsonListUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }
}
