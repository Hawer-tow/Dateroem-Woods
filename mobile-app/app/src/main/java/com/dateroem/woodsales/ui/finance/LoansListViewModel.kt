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

data class LoansListData(
    val loans: List<JsonMap>,
    val totalTaken: Double,
    val outstanding: Double,
    // "suggestions on if its ok to totally pay the loans based on the
    // profit" - moved here from the old Trip Detail "Financials" card,
    // since it's a loans-specific figure, not a general trip detail.
    val suggestion: String
)

sealed interface LoansListUiState {
    data object Loading : LoansListUiState
    data class Loaded(val data: LoansListData) : LoansListUiState
    data class Error(val message: String) : LoansListUiState
}

// "when one clicks the loans you go in and there you can create a new
// loan based on a certain trip" - the Loans tile in Loans & Finance opens
// straight into this list, with New Loan reachable right from here.
class LoansListViewModel(private val api: ApiService, private val tripId: Int) : ViewModel() {

    private val _uiState = MutableStateFlow<LoansListUiState>(LoansListUiState.Loading)
    val uiState: StateFlow<LoansListUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = LoansListUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                LoansListUiState.Loaded(loadData())
            } catch (e: Exception) {
                LoansListUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    private suspend fun loadData(): LoansListData {
        val loans = api.tripLoans(tripId)
        val totalTaken = loans.sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 }
        var totalRepaid = 0.0
        for (loan in loans) {
            val loanId = (loan["loanId"] as? Number)?.toInt() ?: continue
            totalRepaid += api.loanRepayments(loanId).sumOf { (it["amountPaid"] as? Number)?.toDouble() ?: 0.0 }
        }
        val outstanding = (totalTaken - totalRepaid).coerceAtLeast(0.0)
        val profit = (api.tripSummary(tripId)["profit"] as? Number)?.toDouble() ?: 0.0

        val suggestion = when {
            totalTaken == 0.0 -> "No loans recorded on this trip."
            outstanding <= 0.0 -> "Loans already fully repaid."
            profit >= outstanding -> {
                val spare = profit - outstanding
                "Profit (KES $profit) covers the outstanding loan balance (KES $outstanding) with KES $spare to spare - safe to clear it now."
            }
            profit > 0.0 -> "Profit (KES $profit) only covers part of the outstanding balance (KES $outstanding) - a partial repayment up to the profit amount is safe; paying more would dip into other funds."
            else -> "No profit yet on this trip - clearing loans now would come entirely from other funds. Not recommended until profit improves."
        }

        return LoansListData(loans, totalTaken, outstanding, suggestion)
    }

    class Factory(private val api: ApiService, private val tripId: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LoansListViewModel(api, tripId) as T
    }
}
