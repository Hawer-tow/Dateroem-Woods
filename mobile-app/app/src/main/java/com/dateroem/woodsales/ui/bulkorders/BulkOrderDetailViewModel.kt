package com.dateroem.woodsales.ui.bulkorders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.model.FinalizeBulkDeliveryRequest
import com.dateroem.woodsales.data.network.ApiService
import com.dateroem.woodsales.data.network.JsonMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import com.dateroem.woodsales.ui.common.friendlyError

data class BulkOrderDetailData(val order: JsonMap, val production: List<JsonMap>)

sealed interface BulkOrderDetailUiState {
    data object Loading : BulkOrderDetailUiState
    data class Loaded(val data: BulkOrderDetailData) : BulkOrderDetailUiState
    data class Error(val message: String) : BulkOrderDetailUiState
}

data class FinalizeFormState(
    val hardCashAmount: String = "",
    val softCashAmount: String = "",
    val productType: String = "",
    val productDescription: String = "",
    val productMeasurement: String = "",
    val totalBodyFeet: String = "",
    val productTotal: String = ""
)

// "once the bulk delivery is delivered the details are filled on amount
// cash soft which is filled into the bulk delivery as well as the bulk
// delivery description table ... if the payment is to be done through a
// push one can click a send stk push".
class BulkOrderDetailViewModel(
    private val api: ApiService,
    private val bulkDeliveryId: Int,
    private val actorUserId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow<BulkOrderDetailUiState>(BulkOrderDetailUiState.Loading)
    val uiState: StateFlow<BulkOrderDetailUiState> = _uiState.asStateFlow()

    private val _form = MutableStateFlow(FinalizeFormState())
    val form: StateFlow<FinalizeFormState> = _form.asStateFlow()

    private val _finalizing = MutableStateFlow(false)
    val finalizing: StateFlow<Boolean> = _finalizing.asStateFlow()

    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting.asStateFlow()

    private val _finalized = MutableStateFlow(false)
    val finalized: StateFlow<Boolean> = _finalized.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = BulkOrderDetailUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                val order = api.getBulkOrder(bulkDeliveryId)
                val production = api.bulkOrderProduction(bulkDeliveryId)
                BulkOrderDetailUiState.Loaded(BulkOrderDetailData(order, production))
            } catch (e: Exception) {
                BulkOrderDetailUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    // "provide a place to allow one to edit since one might have missed on
    // some issues" - loads the already-recorded amounts into the form so
    // Edit Payment starts from what's on file, not blank fields.
    fun startEditingPayment(order: JsonMap) {
        _form.value = _form.value.copy(
            hardCashAmount = moneyText(order["hardCashAmount"]),
            softCashAmount = moneyText(order["softCashAmount"])
        )
    }

    private fun moneyText(v: Any?): String {
        val n = (v as? Number)?.toDouble() ?: return ""
        return if (n == 0.0) "" else if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()
    }

    fun updateHardCash(v: String) { _form.value = _form.value.copy(hardCashAmount = v) }
    fun updateSoftCash(v: String) { _form.value = _form.value.copy(softCashAmount = v) }
    fun updateProductType(v: String) { _form.value = _form.value.copy(productType = v) }
    fun updateProductDescription(v: String) { _form.value = _form.value.copy(productDescription = v) }
    fun updateProductMeasurement(v: String) { _form.value = _form.value.copy(productMeasurement = v) }
    fun updateTotalBodyFeet(v: String) { _form.value = _form.value.copy(totalBodyFeet = v) }
    fun updateProductTotal(v: String) { _form.value = _form.value.copy(productTotal = v) }

    fun finalize() {
        val f = _form.value
        _error.value = null
        _finalizing.value = true
        viewModelScope.launch {
            try {
                api.finalizeBulkOrder(
                    bulkDeliveryId,
                    FinalizeBulkDeliveryRequest(
                        hardCashAmount = f.hardCashAmount.toDoubleOrNull() ?: 0.0,
                        softCashAmount = f.softCashAmount.toDoubleOrNull() ?: 0.0,
                        productType = f.productType.trim().ifBlank { null },
                        productDescription = f.productDescription.trim().ifBlank { null },
                        productMeasurement = f.productMeasurement.trim().ifBlank { null },
                        totalBodyFeet = f.totalBodyFeet.toDoubleOrNull(),
                        productTotal = f.productTotal.toDoubleOrNull()
                    ),
                    actorUserId
                )
                _finalizing.value = false
                _finalized.value = true
                refresh()
            } catch (e: Exception) {
                _finalizing.value = false
                _error.value = describeError(e)
            }
        }
    }

    fun delete() {
        _deleting.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                api.deleteBulkOrder(bulkDeliveryId, actorUserId)
                _deleting.value = false
                _deleted.value = true
            } catch (e: Exception) {
                _deleting.value = false
                _error.value = describeError(e)
            }
        }
    }

    // Soft-cash amount as typed so far, to hand off to the STK Push
    // screen - "one can click a send stk push and the user will be sent
    // to the stk push in transactions."
    fun currentSoftCashAmount(): Double? = _form.value.softCashAmount.toDoubleOrNull()

    private fun describeError(e: Exception): String {
        if (e is HttpException) {
            val body = e.response()?.errorBody()?.string()
            return friendlyError(e, "Something went wrong. Please try again.", body)
        }
        return friendlyError(e, "Something went wrong")
    }

    class Factory(
        private val api: ApiService,
        private val bulkDeliveryId: Int,
        private val actorUserId: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BulkOrderDetailViewModel(api, bulkDeliveryId, actorUserId) as T
    }
}
