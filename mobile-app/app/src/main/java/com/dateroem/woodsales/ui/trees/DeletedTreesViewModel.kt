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

sealed interface DeletedTreesUiState {
    data object Loading : DeletedTreesUiState
    data class Loaded(val trees: List<Tree>) : DeletedTreesUiState
    data class Error(val message: String) : DeletedTreesUiState
}

// "delete trees should also be created and add a bin just like the
// product section does it" - same recoverable-bin pattern as
// DeletedProductsViewModel.
class DeletedTreesViewModel(private val api: ApiService, private val actorUserId: Int) : ViewModel() {

    private val _uiState = MutableStateFlow<DeletedTreesUiState>(DeletedTreesUiState.Loading)
    val uiState: StateFlow<DeletedTreesUiState> = _uiState.asStateFlow()

    private val _restoringIds = MutableStateFlow<Set<Int>>(emptySet())
    val restoringIds: StateFlow<Set<Int>> = _restoringIds.asStateFlow()

    private val _restoreError = MutableStateFlow<String?>(null)
    val restoreError: StateFlow<String?> = _restoreError.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = DeletedTreesUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                DeletedTreesUiState.Loaded(api.listDeletedTrees())
            } catch (e: Exception) {
                DeletedTreesUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    fun restore(treesId: Int) {
        _restoringIds.value = _restoringIds.value + treesId
        viewModelScope.launch {
            try {
                api.restoreTree(treesId, actorUserId)
                refresh()
            } catch (e: Exception) {
                _restoreError.value = friendlyError(e, "Couldn't restore this tree registration")
            } finally {
                _restoringIds.value = _restoringIds.value - treesId
            }
        }
    }

    fun dismissRestoreError() {
        _restoreError.value = null
    }

    class Factory(private val api: ApiService, private val actorUserId: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DeletedTreesViewModel(api, actorUserId) as T
    }
}
