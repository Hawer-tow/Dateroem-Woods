package com.dateroem.woodsales.ui.finance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dateroem.woodsales.data.network.JsonMap
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodFab

@Composable
fun LoansListScreen(
    viewModel: LoansListViewModel,
    onAddLoan: () -> Unit,
    onSelectLoan: (loanId: Int, amount: Double) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = DateroemBackground,
        floatingActionButton = {
            WoodFab(onClick = onAddLoan) {
                Icon(Icons.Filled.Add, contentDescription = "New loan")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when (val current = state) {
                is LoansListUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
                }
                is LoansListUiState.Error -> {
                    Column(modifier = Modifier.align(Alignment.Center)) {
                        Text("Couldn't load loans", color = MaterialTheme.colorScheme.error)
                        Text(current.message, color = DateroemTextMuted, modifier = Modifier.padding(top = 4.dp))
                        Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Retry")
                        }
                    }
                }
                is LoansListUiState.Loaded -> {
                    val data = current.data
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            "Taken: KES ${data.totalTaken}   Outstanding: KES ${data.outstanding}",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            data.suggestion,
                            color = DateroemAccent,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                        )

                        if (data.loans.isEmpty()) {
                            Text(
                                "No loans for this trip yet - tap + to add one.",
                                color = DateroemTextMuted,
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp)
                            )
                        } else {
                            LazyColumn {
                                items(data.loans) { loan ->
                                    LoanRow(loan, onClick = {
                                        val loanId = (loan["loanId"] as? Number)?.toInt()
                                        val amount = (loan["amount"] as? Number)?.toDouble()
                                        if (loanId != null) onSelectLoan(loanId, amount ?: 0.0)
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoanRow(loan: JsonMap, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("KES ${loan["amount"]}", color = DateroemAccent)
            // Quick analysis right on the row: what's been paid and what's left.
            Text(
                "Paid KES ${money(loan["paidTotal"])}  ·  Remainder KES ${money(loan["remaining"])}",
                color = DateroemTextMuted,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "${loan["description"] ?: loan["category"] ?: "Loan"} - ${loan["status"] ?: "?"}",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun money(value: Any?): String {
    val n = (value as? Number)?.toDouble() ?: return "0"
    return if (n % 1.0 == 0.0) "%,d".format(n.toLong()) else "%,.2f".format(n)
}
