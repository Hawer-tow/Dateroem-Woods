package com.dateroem.woodsales.ui.trips

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
import androidx.compose.ui.graphics.Color
import com.dateroem.woodsales.data.network.JsonMap
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodFab

@Composable
fun TripsScreen(viewModel: TripsViewModel, onAddTrip: () -> Unit, onSelectTrip: (Int) -> Unit) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = DateroemBackground,
        floatingActionButton = {
            WoodFab(onClick = onAddTrip) {
                Icon(Icons.Filled.Add, contentDescription = "Create trip")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when (val current = state) {
                is TripsUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
                }
                is TripsUiState.Error -> {
                    Column(modifier = Modifier.align(Alignment.Center)) {
                        Text("Couldn't load trips", color = MaterialTheme.colorScheme.error)
                        Text(current.message, color = DateroemTextMuted, modifier = Modifier.padding(top = 4.dp))
                        Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Retry")
                        }
                    }
                }
                is TripsUiState.Loaded -> {
                    if (current.trips.isEmpty()) {
                        Text(
                            "No trips yet - tap + to create one. Almost every other section needs at least one trip.",
                            color = DateroemTextMuted,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
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

// Light blue accent for a trip that's still active (no endDate yet) - a
// quick visual cue among a longer list of already-concluded trips.
// Translucent (not solid) so the existing light text colors, tuned for
// this app's dark surfaces, stay readable on top of it.
private val ActiveTripTint = Color(0xFF29B6F6).copy(alpha = 0.28f)

@Composable
private fun TripRow(trip: JsonMap, onClick: () -> Unit) {
    val isActive = trip["endDate"] == null
    Card(
        colors = CardDefaults.cardColors(containerColor = if (isActive) ActiveTripTint else DateroemSurface),
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
            Text(
                "KES ${trip["totalPayment"] ?: 0} total payment",
                color = DateroemTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
