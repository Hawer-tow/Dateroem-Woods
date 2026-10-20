package com.dateroem.woodsales.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.model.PayLoanRequest
import com.dateroem.woodsales.data.network.ApiService
import com.dateroem.woodsales.ui.transactions.PayoutController

// "for each loan there will be an option to click it and an option pay
// loan in it ... led to the transactions area and the option loan
// repayment for the selected loan and its id is selected based on the
// trip id" - loanId is fixed context (from tapping the loan), creates a
// fresh LOAN_REPAYMENTS row and sends it. Admin-only, enforced server-side.
// The lender's phone is looked up from the loan and the M-Pesa receipt fills
// itself in - see PayoutController.
class PayLoanViewModel(
    api: ApiService,
    loanId: Int,
    val remainingBalance: Double,
    actorUserId: Int
) : ViewModel() {

    val flow = PayoutController(
        api = api,
        scope = viewModelScope,
        actorUserId = actorUserId,
        recipientTarget = "LOAN",
        recipientRowId = loanId,
        prefilledAmount = remainingBalance.takeIf { it > 0 }
    ) { amount, phone -> api.payLoan(loanId, PayLoanRequest(amount, phone), actorUserId) }

    class Factory(
        private val api: ApiService,
        private val loanId: Int,
        private val remainingBalance: Double,
        private val actorUserId: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PayLoanViewModel(api, loanId, remainingBalance, actorUserId) as T
    }
}
