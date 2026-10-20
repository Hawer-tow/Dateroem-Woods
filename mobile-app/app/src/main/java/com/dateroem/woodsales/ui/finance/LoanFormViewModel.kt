package com.dateroem.woodsales.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.model.LoanRequest
import com.dateroem.woodsales.data.model.UpdateLoanRequest
import com.dateroem.woodsales.data.network.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import com.dateroem.woodsales.ui.common.friendlyError

data class LoanFormState(
    val loading: Boolean = false,
    val amount: String = "",
    val description: String = "",
    val category: String = "",
    val status: String = "ACTIVE",
    val submitting: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

// Create-or-edit for one LOAN row - "allow one to access loans ... or even
// edit and add a new loan", admin-only (enforced server-side too).
class LoanFormViewModel(
    private val api: ApiService,
    private val tripId: Int,
    private val actorUserId: Int,
    private val editingLoanId: Int?
) : ViewModel() {

    private val _state = MutableStateFlow(LoanFormState(loading = editingLoanId != null))
    val state: StateFlow<LoanFormState> = _state.asStateFlow()

    init {
        editingLoanId?.let { id ->
            viewModelScope.launch {
                try {
                    // No single-loan GET on the client side yet - the trip's
                    // loan list is small, so find it there (same pattern as
                    // TreeFormViewModel).
                    val loan = api.tripLoans(tripId).firstOrNull { (it["loanId"] as? Number)?.toInt() == id }
                    if (loan != null) {
                        _state.value = _state.value.copy(
                            loading = false,
                            amount = (loan["amount"] as? Number)?.toString().orEmpty(),
                            description = (loan["description"] as? String).orEmpty(),
                            category = (loan["category"] as? String).orEmpty(),
                            status = (loan["status"] as? String) ?: "ACTIVE"
                        )
                    } else {
                        _state.value = _state.value.copy(loading = false)
                    }
                } catch (e: Exception) {
                    _state.value = _state.value.copy(loading = false, error = friendlyError(e, "Couldn't load this loan"))
                }
            }
        }
    }

    fun updateAmount(v: String) { _state.value = _state.value.copy(amount = v) }
    fun updateDescription(v: String) { _state.value = _state.value.copy(description = v) }
    fun updateCategory(v: String) { _state.value = _state.value.copy(category = v) }
    fun updateStatus(v: String) { _state.value = _state.value.copy(status = v) }

    fun submit() {
        val s = _state.value
        val amount = s.amount.toDoubleOrNull()
        if (amount == null) {
            _state.value = s.copy(error = "Enter a valid amount")
            return
        }

        _state.value = s.copy(submitting = true, error = null)
        viewModelScope.launch {
            try {
                if (editingLoanId != null) {
                    api.updateLoan(
                        editingLoanId,
                        UpdateLoanRequest(
                            amount = amount,
                            description = s.description.trim().ifBlank { null },
                            category = s.category.trim().ifBlank { null },
                            status = s.status.trim().ifBlank { null }
                        ),
                        actorUserId
                    )
                } else {
                    api.createLoan(
                        tripId,
                        LoanRequest(
                            amount = amount,
                            description = s.description.trim().ifBlank { null },
                            category = s.category.trim().ifBlank { null }
                        ),
                        actorUserId
                    )
                }
                _state.value = _state.value.copy(submitting = false, saved = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false, error = describeError(e))
            }
        }
    }

    private fun describeError(e: Exception): String {
        if (e is HttpException) {
            val body = e.response()?.errorBody()?.string()
            return friendlyError(e, "Something went wrong. Please try again.", body)
        }
        return friendlyError(e, "Couldn't save this loan")
    }

    class Factory(
        private val api: ApiService,
        private val tripId: Int,
        private val actorUserId: Int,
        private val editingLoanId: Int?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LoanFormViewModel(api, tripId, actorUserId, editingLoanId) as T
    }
}
