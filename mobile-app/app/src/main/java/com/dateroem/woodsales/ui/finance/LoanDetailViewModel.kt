package com.dateroem.woodsales.ui.finance

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

sealed interface LoanDetailUiState {
    data object Loading : LoanDetailUiState
    data class Loaded(
        val repayments: List<JsonMap>,
        val borrowed: Double,
        val totalRepaid: Double,
        // Sent through Send Money but M-Pesa hasn't confirmed yet - not counted as paid.
        val pending: Double,
        val remaining: Double
    ) : LoanDetailUiState
    data class Error(val message: String) : LoanDetailUiState
}

// "for each loan there will be an option to click it and an option pay
// loan in it if the loan repayments have not yet reached to the expected
// amount to clear loan" - loanAmount comes from the FinanceOverviewScreen
// row that was tapped (already loaded there), avoiding a second
// single-loan lookup endpoint.
class LoanDetailViewModel(private val api: ApiService, private val loanId: Int, private val loanAmount: Double) : ViewModel() {

    private val _uiState = MutableStateFlow<LoanDetailUiState>(LoanDetailUiState.Loading)
    val uiState: StateFlow<LoanDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = LoanDetailUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                val repayments = api.loanRepayments(loanId)
                // A repayment only counts once it really is paid: one still waiting on
                // M-Pesa is PENDING and shown separately (a failed payout is removed).
                fun amountOf(r: JsonMap) = (r["amountPaid"] as? Number)?.toDouble() ?: 0.0
                val totalRepaid = repayments.filter { it["state"] != "PENDING" }.sumOf { amountOf(it) }
                val pending = repayments.filter { it["state"] == "PENDING" }.sumOf { amountOf(it) }
                LoanDetailUiState.Loaded(repayments, loanAmount, totalRepaid, pending, (loanAmount - totalRepaid).coerceAtLeast(0.0))
            } catch (e: Exception) {
                LoanDetailUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    class Factory(private val api: ApiService, private val loanId: Int, private val loanAmount: Double) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LoanDetailViewModel(api, loanId, loanAmount) as T
    }
}
