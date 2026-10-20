package com.dateroem.woodsales.ui.trees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.model.Tree
import com.dateroem.woodsales.data.network.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.dateroem.woodsales.ui.common.friendlyError

sealed interface TreesUiState {
    data object Loading : TreesUiState
    data class Loaded(val trees: List<Tree>) : TreesUiState
    data class Error(val message: String) : TreesUiState
}

// "an area in products to select from trees already registered for a
// certain trip" - this is that registration list itself, scoped to
// whichever trip is active.
class TreesViewModel(private val api: ApiService, private val tripId: Int) : ViewModel() {

    private val _uiState = MutableStateFlow<TreesUiState>(TreesUiState.Loading)
    val uiState: StateFlow<TreesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = TreesUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                TreesUiState.Loaded(api.listTrees(tripId))
            } catch (e: Exception) {
                TreesUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    class Factory(private val api: ApiService, private val tripId: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TreesViewModel(api, tripId) as T
    }
}
