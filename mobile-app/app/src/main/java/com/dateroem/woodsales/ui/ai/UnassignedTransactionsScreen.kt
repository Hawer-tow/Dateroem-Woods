package com.dateroem.woodsales.ui.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.dateroem.woodsales.ui.common.ErrorBox

@Composable
fun UnassignedTransactionsScreen(viewModel: UnassignedTransactionsViewModel, onOpen: (JsonMap) -> Unit) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(containerColor = DateroemBackground) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when (val current = state) {
                is UnassignedTransactionsUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
                }
                is UnassignedTransactionsUiState.Error -> {
                    Column(modifier = Modifier.align(Alignment.Center)) {
                        Text("Couldn't load unassigned transactions", color = MaterialTheme.colorScheme.error)
                        ErrorBox(current.message, modifier = Modifier.padding(top = 8.dp))
                        Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Retry")
                        }
                    }
                }
                is UnassignedTransactionsUiState.Loaded -> {
                    if (current.transactions.isEmpty()) {
                        Text(
                            "Nothing unassigned right now.",
                            color = DateroemTextMuted,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn {
                            items(current.transactions) { row ->
                                TransactionRow(row, onClick = { onOpen(row) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(row: JsonMap, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("KES ${row["amount"]}", color = DateroemAccent)
            Text(
                "${row["paymentMode"] ?: "?"} - Transaction #${row["financialTransactionId"]}",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall
            )
            Text("Recorded: ${row["createdAt"] ?: "?"}", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
