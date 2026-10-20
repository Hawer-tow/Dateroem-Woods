package com.dateroem.woodsales.data.model

// "allow one to access loans and see the various loans used for that trip
// or even edit and add a new loan" - the list itself stays JsonMap (server
// shape still evolving, see ApiService's JsonMap note), these are just the
// create/edit request bodies.
data class LoanRequest(
    val amount: Double,
    val description: String?,
    val category: String?
)

data class UpdateLoanRequest(
    val amount: Double,
    val description: String?,
    val category: String?,
    val status: String?
)
