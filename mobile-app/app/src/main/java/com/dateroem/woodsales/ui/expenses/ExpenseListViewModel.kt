package com.dateroem.woodsales.ui.expenses

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
import com.dateroem.woodsales.ui.common.friendlyError

enum class ExpenseFilter(val apiValue: String, val label: String) {
    ALL("all", "All"),
    PENDING("pending", "Pending"),
    ASSIGNED("assigned", "Assigned")
}

sealed interface ExpenseListUiState {
    data object Loading : ExpenseListUiState
    data class Loaded(val items: List<JsonMap>) : ExpenseListUiState
    data class Error(val message: String) : ExpenseListUiState
}

// business rules: "2 sections where in expenses where one can toggle to
// the all or assigned ones so one can see only the assigned transactions".
class ExpenseListViewModel(
    private val api: ApiService,
    private val tripId: Int,
    private val category: ExpenseCategory,
    private val actorUserId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExpenseListUiState>(ExpenseListUiState.Loading)
    val uiState: StateFlow<ExpenseListUiState> = _uiState.asStateFlow()

    private val _filter = MutableStateFlow(ExpenseFilter.ALL)
    val filter: StateFlow<ExpenseFilter> = _filter.asStateFlow()

    private val _deletingIds = MutableStateFlow<Set<Int>>(emptySet())
    val deletingIds: StateFlow<Set<Int>> = _deletingIds.asStateFlow()

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError.asStateFlow()

    // "prompt if its not assigned, just click on it and assign on it" - the
    // unpaid entry being matched, and the not-yet-assigned payments to pick from.
    data class AssignPicker(
        val rowId: Int,
        val amount: Double,
        val loading: Boolean = true,
        val candidates: List<JsonMap> = emptyList(),
        val busy: Boolean = false,
        val error: String? = null
    )

    private val _assignPicker = MutableStateFlow<AssignPicker?>(null)
    val assignPicker: StateFlow<AssignPicker?> = _assignPicker.asStateFlow()

    init {
        refresh()
    }

    fun openAssign(rowId: Int, amount: Double) {
        _assignPicker.value = AssignPicker(rowId, amount)
        viewModelScope.launch {
            _assignPicker.value = try {
                // Closest to this entry's amount first.
                val sorted = api.unassignedTransactions().sortedBy {
                    kotlin.math.abs(((it["amount"] as? Number)?.toDouble() ?: 0.0) - amount)
                }
                AssignPicker(rowId, amount, loading = false, candidates = sorted)
            } catch (e: Exception) {
                AssignPicker(rowId, amount, loading = false, error = friendlyError(e, "Couldn't load unassigned payments"))
            }
        }
    }

    fun closeAssign() {
        _assignPicker.value = null
    }

    fun assign(financialTransactionId: Int) {
        val picker = _assignPicker.value ?: return
        _assignPicker.value = picker.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                api.assignTransaction(
                    financialTransactionId,
                    AssignTransactionRequest(category.name, picker.rowId),
                    actorUserId
                )
                _assignPicker.value = null
                refresh()
            } catch (e: Exception) {
                _assignPicker.value = picker.copy(busy = false, error = friendlyError(e, "Couldn't assign this payment"))
            }
        }
    }

    fun setFilter(value: ExpenseFilter) {
        _filter.value = value
        refresh()
    }

    fun refresh() {
        _uiState.value = ExpenseListUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                ExpenseListUiState.Loaded(api.expenseLineItems(tripId, category.key, _filter.value.apiValue))
            } catch (e: Exception) {
                ExpenseListUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    fun delete(rowId: Int) {
        _deletingIds.value = _deletingIds.value + rowId
        _deleteError.value = null
        viewModelScope.launch {
            try {
                api.deleteExpense(tripId, category.key, rowId, actorUserId)
                refresh()
            } catch (e: Exception) {
                _deleteError.value = friendlyError(e, "Couldn't delete this entry")
            } finally {
                _deletingIds.value = _deletingIds.value - rowId
            }
        }
    }

    class Factory(
        private val api: ApiService,
        private val tripId: Int,
        private val category: ExpenseCategory,
        private val actorUserId: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ExpenseListViewModel(api, tripId, category, actorUserId) as T
    }
}
