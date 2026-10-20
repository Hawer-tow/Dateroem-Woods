package com.dateroem.woodsales.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.model.Product
import com.dateroem.woodsales.data.network.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.dateroem.woodsales.ui.common.friendlyError

sealed interface DeletedProductsUiState {
    data object Loading : DeletedProductsUiState
    data class Loaded(val products: List<Product>) : DeletedProductsUiState
    data class Error(val message: String) : DeletedProductsUiState
}

class DeletedProductsViewModel(private val api: ApiService, private val actorUserId: Int) : ViewModel() {

    private val _uiState = MutableStateFlow<DeletedProductsUiState>(DeletedProductsUiState.Loading)
    val uiState: StateFlow<DeletedProductsUiState> = _uiState.asStateFlow()

    private val _restoringIds = MutableStateFlow<Set<Int>>(emptySet())
    val restoringIds: StateFlow<Set<Int>> = _restoringIds.asStateFlow()

    private val _restoreError = MutableStateFlow<String?>(null)
    val restoreError: StateFlow<String?> = _restoreError.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = DeletedProductsUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                DeletedProductsUiState.Loaded(api.listDeletedProducts())
            } catch (e: Exception) {
                DeletedProductsUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    fun restore(productId: Int) {
        _restoringIds.value = _restoringIds.value + productId
        viewModelScope.launch {
            // "deleted products restore causes the app to crash" - this had
            // no catch at all, only finally, so any failure (a network
            // blip, a validation error) propagated out of the coroutine
            // uncaught and took the whole app down instead of just showing
            // an error.
            try {
                api.restoreProduct(productId, actorUserId)
                refresh()
            } catch (e: Exception) {
                _restoreError.value = friendlyError(e, "Couldn't restore this product")
            } finally {
                _restoringIds.value = _restoringIds.value - productId
            }
        }
    }

    fun dismissRestoreError() {
        _restoreError.value = null
    }

    class Factory(private val api: ApiService, private val actorUserId: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DeletedProductsViewModel(api, actorUserId) as T
    }
}
