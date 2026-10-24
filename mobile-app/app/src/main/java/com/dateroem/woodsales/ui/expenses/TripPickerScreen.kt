package com.dateroem.woodsales.ui.expenses

import androidx.compose.foundation.background
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
fun TripPickerScreen(viewModel: TripPickerViewModel, onSelectTrip: (Int) -> Unit) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(DateroemBackground).padding(16.dp)) {
        when (val current = state) {
            is TripPickerUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
            }
            is TripPickerUiState.Error -> {
                Column(modifier = Modifier.align(Alignment.Center)) {
                    Text("Couldn't load trips", color = MaterialTheme.colorScheme.error)
                    ErrorBox(current.message, modifier = Modifier.padding(top = 8.dp))
                    Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Retry")
                    }
                }
            }
            is TripPickerUiState.Loaded -> {
                if (current.trips.isEmpty()) {
                    Text(
                        "No trips recorded yet - create one from the Trips section first.",
                        color = DateroemTextMuted,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            "Pick a trip to see its expense categories",
                            color = DateroemTextMuted,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        LazyColumn {
                            items(current.trips) { trip ->
                                TripRow(trip, onClick = {
                                    (trip["tripId"] as? Number)?.toInt()?.let(onSelectTrip)
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
private fun TripRow(trip: JsonMap, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Trip #${trip["tripId"]}", color = MaterialTheme.colorScheme.onSurface)
            Text(
                "${trip["startDate"] ?: "?"} - ${trip["endDate"] ?: "ongoing"}",
                color = DateroemTextMuted,
                style = MaterialTheme.typography.bodySmall
            )
            (trip["productDescription"] as? String)?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = DateroemAccent, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
