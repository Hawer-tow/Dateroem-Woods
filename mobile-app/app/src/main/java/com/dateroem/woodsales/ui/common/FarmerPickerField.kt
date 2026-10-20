package com.dateroem.woodsales.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.dateroem.woodsales.data.model.Farmer
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted

// "knowing a farmer id and it might be a new farmer is hard just select
// the farmer the system checks for that farmer and the id is filled" -
// a proper scrollable list instead of typing an ID, everywhere a farmer
// needs picking. "if the farmers in that list are more than 15 provide a
// search area where the user types the first names ... or even the phone
// number" - the threshold and dual-field search below.
private const val SEARCH_THRESHOLD = 15

@Composable
fun FarmerPickerField(
    label: String,
    farmers: List<Farmer>,
    selectedFarmerId: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    allowNone: Boolean = true,
    noneLabel: String = "(none)"
) {
    var showPicker by remember { mutableStateOf(false) }
    val selected = farmers.firstOrNull { it.farmerId == selectedFarmerId }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showPicker = true }
            .background(DateroemSurface, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Column {
            Text(label, color = DateroemTextMuted, style = MaterialTheme.typography.labelSmall)
            Text(selected?.name ?: noneLabel, color = DateroemText)
        }
    }

    if (showPicker) {
        FarmerPickerDialog(
            farmers = farmers,
            allowNone = allowNone,
            noneLabel = noneLabel,
            onSelect = {
                onSelect(it)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun FarmerPickerDialog(
    farmers: List<Farmer>,
    allowNone: Boolean,
    noneLabel: String,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(farmers, query) {
        if (query.isBlank()) {
            farmers
        } else {
            farmers.filter {
                (it.name?.contains(query, ignoreCase = true) == true) || (it.phoneNo?.contains(query) == true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DateroemBackground),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Farmer", color = DateroemText, style = MaterialTheme.typography.titleMedium)

                // "provide a clear list so that its obvious its a list" -
                // a real scrollable list in its own dialog, not a cramped
                // dropdown menu.
                if (farmers.size > SEARCH_THRESHOLD) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search by name or phone") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DateroemAccent,
                            unfocusedBorderColor = DateroemTextMuted.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    )
                }

                LazyColumn(modifier = Modifier.padding(top = 10.dp)) {
                    if (allowNone) {
                        item {
                            FarmerRow(name = noneLabel, subtitle = null, onClick = { onSelect(null) })
                        }
                    }
                    items(filtered) { farmer ->
                        FarmerRow(
                            name = farmer.name ?: "Unnamed",
                            subtitle = farmer.phoneNo,
                            onClick = { onSelect(farmer.farmerId) }
                        )
                    }
                    if (filtered.isEmpty() && query.isNotBlank()) {
                        item {
                            Text(
                                "No farmers match \"$query\".",
                                color = DateroemTextMuted,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FarmerRow(name: String, subtitle: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(name, color = DateroemText)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
