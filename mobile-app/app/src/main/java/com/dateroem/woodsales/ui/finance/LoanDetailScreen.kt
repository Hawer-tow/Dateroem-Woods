package com.dateroem.woodsales.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.data.network.JsonMap
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodButton

@Composable
fun LoanDetailScreen(
    viewModel: LoanDetailViewModel,
    isAdmin: Boolean,
    onPayLoan: (remaining: Double) -> Unit,
    onEditLoan: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(DateroemBackground).padding(16.dp)) {
        when (val current = state) {
            is LoanDetailUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
            }
            is LoanDetailUiState.Error -> {
                Column(modifier = Modifier.align(Alignment.Center)) {
                    Text("Couldn't load repayments", color = MaterialTheme.colorScheme.error)
                    Text(current.message, color = DateroemTextMuted, modifier = Modifier.padding(top = 4.dp))
                    Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Retry")
                    }
                }
            }
            is LoanDetailUiState.Loaded -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        "Borrowed: KES ${current.borrowed}",
                        color = DateroemTextMuted,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Text(
                        "Paid: KES ${current.totalRepaid}",
                        color = DateroemText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Remainder: KES ${current.remaining}",
                        color = if (current.remaining > 0) DateroemAccent else DateroemTextMuted,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                    )
                    if (current.pending > 0) {
                        Text(
                            "KES ${current.pending} more is on its way - waiting for M-Pesa to confirm, so it isn't counted as paid yet.",
                            color = DateroemTextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    if (isAdmin) {
                        TextButton(
                            onClick = onEditLoan,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text("Edit Loan Details", color = DateroemAccent)
                        }
                    }

                    if (isAdmin && current.remaining > 0 && current.pending <= 0) {
                        WoodButton(
                            onClick = { onPayLoan(current.remaining) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            Text("Pay Loan", fontWeight = FontWeight.Bold)
                        }
                    } else if (current.remaining <= 0 && current.pending <= 0) {
                        Text(
                            "This loan is fully repaid.",
                            color = DateroemAccent,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    Text("Repayments", color = DateroemText, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                    if (current.repayments.isEmpty()) {
                        Text("No repayments yet.", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall)
                    } else {
                        current.repayments.forEach { repayment -> RepaymentRow(repayment) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepaymentRow(repayment: JsonMap) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("KES ${repayment["amountPaid"]}", color = DateroemAccent)
            Text(
                "${repayment["paymentDate"] ?: "?"} - ${if (repayment["state"] == "PENDING") "PENDING - waiting for M-Pesa" else (repayment["referenceId"] ?: "recorded")}",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
