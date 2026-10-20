package com.dateroem.woodsales.ui.bulkorders

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
fun BulkOrdersScreen(viewModel: BulkOrdersViewModel, onAdd: () -> Unit, onSelect: (Int) -> Unit) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = DateroemBackground,
        floatingActionButton = {
            WoodFab(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "New heavy order")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when (val current = state) {
                is BulkOrdersUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
                }
                is BulkOrdersUiState.Error -> {
                    Column(modifier = Modifier.align(Alignment.Center)) {
                        Text("Couldn't load heavy orders", color = MaterialTheme.colorScheme.error)
                        Text(current.message, color = DateroemTextMuted, modifier = Modifier.padding(top = 4.dp))
                        Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Retry")
                        }
                    }
                }
                is BulkOrdersUiState.Loaded -> {
                    if (current.orders.isEmpty()) {
                        Text(
                            "No heavy orders yet - tap + to search/negotiate one.",
                            color = DateroemTextMuted,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn {
                            items(current.orders) { order ->
                                BulkOrderRow(order, onClick = {
                                    (order["bulkDeliveryId"] as? Number)?.toInt()?.let(onSelect)
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BulkOrderRow(order: JsonMap, onClick: () -> Unit) {
    val delivered = order["deliveredAt"] != null
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(order["deliveredTo"] as? String ?: "Unnamed company", color = MaterialTheme.colorScheme.onSurface)
            Text(
                if (delivered) "Delivered - KES ${order["amountPaid"]}" else "Awaiting delivery",
                color = if (delivered) DateroemAccent else DateroemTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
