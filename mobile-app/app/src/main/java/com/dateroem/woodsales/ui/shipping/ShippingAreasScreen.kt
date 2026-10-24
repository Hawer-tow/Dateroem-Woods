package com.dateroem.woodsales.ui.shipping

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dateroem.woodsales.data.model.ShippingArea
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodFab
import com.dateroem.woodsales.ui.common.ErrorBox

@Composable
fun ShippingAreasScreen(viewModel: ShippingAreasViewModel, onAdd: () -> Unit, onSelect: (Int) -> Unit) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = DateroemBackground,
        floatingActionButton = {
            WoodFab(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "New delivery area")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when (val current = state) {
                is ShippingAreasUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
                }
                is ShippingAreasUiState.Error -> {
                    Column(modifier = Modifier.align(Alignment.Center)) {
                        Text("Couldn't load delivery areas", color = MaterialTheme.colorScheme.error)
                        ErrorBox(current.message, modifier = Modifier.padding(top = 8.dp))
                        Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Retry")
                        }
                    }
                }
                is ShippingAreasUiState.Loaded -> {
                    if (current.areas.isEmpty()) {
                        Text(
                            "No delivery/pickup areas yet - tap + to add the first one.",
                            color = DateroemTextMuted,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn {
                            items(current.areas) { area ->
                                ShippingAreaRow(area, onClick = { onSelect(area.deliveryAreaId) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShippingAreaRow(area: ShippingArea, onClick: () -> Unit) {
    val active = area.isActive != false
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(area.areaName ?: "Unnamed area", color = DateroemText, fontWeight = FontWeight.Bold)
                Text(area.city ?: "", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall)
                if (!area.description.isNullOrBlank()) {
                    Text(
                        area.description,
                        color = DateroemTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (!active) {
                Text(
                    "Inactive",
                    color = DateroemTextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .background(DateroemTextMuted.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
