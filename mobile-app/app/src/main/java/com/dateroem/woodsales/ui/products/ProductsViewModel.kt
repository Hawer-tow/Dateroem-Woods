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
import retrofit2.HttpException
import com.dateroem.woodsales.ui.common.friendlyError

sealed interface ProductsUiState {
    data object Loading : ProductsUiState
    data class Loaded(val products: List<Product>) : ProductsUiState
    data class Error(val message: String) : ProductsUiState
}

// isAdmin: only admins see the out-of-stock banner and can retire or bring
// back a product (the server checks this too).
class ProductsViewModel(
    private val api: ApiService,
    private val actorUserId: Int,
    val isAdmin: Boolean
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProductsUiState>(ProductsUiState.Loading)
    val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()

    // "in an event where the stock is depleted the admins are notified" -
    // how many products have run out (shown on a banner), and whether the
    // list is currently showing just those.
    private val _depletedCount = MutableStateFlow(0)
    val depletedCount: StateFlow<Int> = _depletedCount.asStateFlow()
    private val _showingOutOfStock = MutableStateFlow(false)
    val showingOutOfStock: StateFlow<Boolean> = _showingOutOfStock.asStateFlow()

    private val _busyIds = MutableStateFlow<Set<Int>>(emptySet())
    val busyIds: StateFlow<Set<Int>> = _busyIds.asStateFlow()
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = ProductsUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                ProductsUiState.Loaded(if (_showingOutOfStock.value) api.listDepletedProducts() else api.listProducts())
            } catch (e: Exception) {
                ProductsUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
        refreshDepletedCount()
    }

    private fun refreshDepletedCount() {
        if (!isAdmin) return
        viewModelScope.launch {
            try {
                _depletedCount.value = api.listDepletedProducts().size
            } catch (_: Exception) {
                // Keep the last count; the list itself shows any connection trouble.
            }
        }
    }

    fun toggleOutOfStock() {
        _showingOutOfStock.value = !_showingOutOfStock.value
        refresh()
    }

    // Retire (hide from the shop) or bring back. The stock and every existing
    // order stay exactly as they are either way.
    fun setRetired(product: Product, retire: Boolean) {
        val id = product.productId
        _busyIds.value = _busyIds.value + id
        viewModelScope.launch {
            try {
                if (retire) api.retireProduct(id, actorUserId) else api.unretireProduct(id, actorUserId)
                refresh()
            } catch (e: Exception) {
                _actionError.value = describe(e, if (retire) "Couldn't retire this product" else "Couldn't bring this product back")
            } finally {
                _busyIds.value = _busyIds.value - id
            }
        }
    }

    fun dismissActionError() {
        _actionError.value = null
    }

    // The server's own reason when it gave one ("Admin/SuperAdmin only", ...).
    private fun describe(e: Exception, fallback: String): String {
        if (e is HttpException) {
            val body = e.response()?.errorBody()?.string().orEmpty()
            Regex("\"(?:detail|message)\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)?.let { return it }
        }
        return friendlyError(e, fallback)
    }

    class Factory(
        private val api: ApiService,
        private val actorUserId: Int,
        private val isAdmin: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ProductsViewModel(api, actorUserId, isAdmin) as T
    }
}
