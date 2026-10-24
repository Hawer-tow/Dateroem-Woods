package com.dateroem.woodsales.ui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dateroem.woodsales.data.network.JsonMap
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.common.ErrorBox

@Composable
fun TopFarmersScreen(viewModel: TopFarmersViewModel) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(containerColor = DateroemBackground) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when (val current = state) {
                is TopFarmersUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
                }
                is TopFarmersUiState.Error -> {
                    Column(modifier = Modifier.align(Alignment.Center)) {
                        Text("Couldn't load top farmers", color = MaterialTheme.colorScheme.error)
                        ErrorBox(current.message, modifier = Modifier.padding(top = 8.dp))
                        Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Retry")
                        }
                    }
                }
                is TopFarmersUiState.Loaded -> {
                    if (current.farmers.isEmpty()) {
                        Text(
                            "No trees recorded against any farmer yet.",
                            color = DateroemTextMuted,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn {
                            itemsIndexed(current.farmers) { index, farmer ->
                                FarmerRow(rank = index + 1, farmer = farmer)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FarmerRow(rank: Int, farmer: JsonMap) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("#$rank", color = DateroemAccent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp))
            Column {
                Text(
                    "${farmer["name"] ?: "Unknown"} - ${farmer["totalTreesSupplied"] ?: 0} trees",
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${farmer["phoneNo"] ?: "no phone"} - across ${farmer["tripCount"] ?: 0} trip(s)",
                    color = DateroemTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
