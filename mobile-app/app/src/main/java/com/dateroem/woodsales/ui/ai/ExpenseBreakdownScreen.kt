package com.dateroem.woodsales.ui.ai

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
fun ExpenseBreakdownScreen(viewModel: ExpenseBreakdownViewModel) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(containerColor = DateroemBackground) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when (val current = state) {
                is ExpenseBreakdownUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
                }
                is ExpenseBreakdownUiState.Error -> {
                    Column(modifier = Modifier.align(Alignment.Center)) {
                        Text("Couldn't load the expense breakdown", color = MaterialTheme.colorScheme.error)
                        ErrorBox(current.message, modifier = Modifier.padding(top = 8.dp))
                        Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Retry")
                        }
                    }
                }
                is ExpenseBreakdownUiState.Loaded -> {
                    if (current.rows.isEmpty()) {
                        Text(
                            "No expenses recorded against any trip yet.",
                            color = DateroemTextMuted,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn {
                            items(current.rows) { row -> ExpenseRow(row) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseRow(row: JsonMap) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("KES ${row["totalAmount"]}", color = DateroemAccent)
            Text(
                "${(row["category"] as? String)?.replaceFirstChar { it.uppercase() } ?: "?"} - ${row["tripDescription"] ?: "Trip #${row["tripId"]}"}",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall
            )
            Text("${row["entryCount"] ?: 0} entries", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
