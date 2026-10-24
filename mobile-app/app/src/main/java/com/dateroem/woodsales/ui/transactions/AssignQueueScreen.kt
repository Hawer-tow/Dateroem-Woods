package com.dateroem.woodsales.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.dateroem.woodsales.ui.theme.WoodButton
import com.dateroem.woodsales.ui.theme.WoodOn
import com.dateroem.woodsales.ui.common.ErrorBox

@Composable
fun AssignQueueScreen(viewModel: AssignQueueViewModel, onOpen: (JsonMap) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val claiming by viewModel.claiming.collectAsState()

    Scaffold(containerColor = DateroemBackground) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            WoodButton(
                onClick = { viewModel.claimMore() },
                enabled = !claiming,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                if (claiming) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = WoodOn)
                } else {
                    Text("Claim Unassigned Transactions")
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (val current = state) {
                    is AssignQueueUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
                    }
                    is AssignQueueUiState.Error -> {
                        Column(modifier = Modifier.align(Alignment.Center)) {
                            Text("Couldn't load the queue", color = MaterialTheme.colorScheme.error)
                            ErrorBox(current.message, modifier = Modifier.padding(top = 8.dp))
                            Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                                Text("Retry")
                            }
                        }
                    }
                    is AssignQueueUiState.Loaded -> {
                        if (current.claimed.isEmpty()) {
                            Text(
                                "Nothing claimed - tap above to pull a batch of unassigned transactions.",
                                color = DateroemTextMuted,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn {
                                items(current.claimed) { row ->
                                    ClaimedRow(
                                        row,
                                        onOpen = { onOpen(row) },
                                        onRelease = {
                                            (row["financialTransactionId"] as? Number)?.toInt()?.let(viewModel::release)
                                        }
                                    )
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
private fun ClaimedRow(row: JsonMap, onOpen: () -> Unit, onRelease: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onOpen)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("KES ${row["amount"]}", color = DateroemAccent)
                Text(
                    "${row["paymentMode"] ?: "?"} - Transaction #${row["financialTransactionId"]}",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall
                )
                (row["referenceId"] as? String)?.let {
                    Text("Ref: $it", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall)
                }
                // "place the date of that transaction as it is in the
                // database for quicker location ... when allocating".
                Text("Recorded: ${row["createdAt"] ?: "?"}", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onRelease) { Text("Release") }
        }
    }
}
