package com.dateroem.woodsales.ui.trees

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.data.model.RemainderTree
import com.dateroem.woodsales.ui.common.FarmerPickerField
import com.dateroem.woodsales.ui.common.SpeakErrorEffect
import com.dateroem.woodsales.ui.common.VoiceAssistantToggle
import com.dateroem.woodsales.ui.common.VoiceOutlinedTextField
import com.dateroem.woodsales.ui.common.currentLocationOrNull
import com.dateroem.woodsales.ui.common.rememberVoiceAssistant
import com.dateroem.woodsales.ui.common.toCoordinateString
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import kotlinx.coroutines.launch
import com.dateroem.woodsales.ui.theme.WoodButton
import com.dateroem.woodsales.ui.theme.WoodOn

@Composable
fun TreeFormScreen(
    viewModel: TreeFormViewModel,
    isEditing: Boolean,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    onCancel: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locatingCoordinates by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val voice = rememberVoiceAssistant()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    LaunchedEffect(state.deleted) {
        if (state.deleted) onDeleted()
    }
    SpeakErrorEffect(voice, state.error)

    // "delete trees should also be created and add a bin just like the
    // product section does it" - same confirm-then-recoverable pattern.
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this tree registration?") },
            text = { Text("It'll disappear from the trees list, but stays recoverable - an admin can restore it from Deleted Trees.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // "allow the coordinate section to be filled automatically by the
    // system checking current coordinates ... even when one wants to edit
    // the trees then one can update the coordinates" - works for both a
    // fresh registration and refreshing an existing one's location.
    fun fetchCurrentLocation() {
        locationError = null
        locatingCoordinates = true
        scope.launch {
            val location = currentLocationOrNull(context)
            locatingCoordinates = false
            if (location != null) {
                viewModel.updateCoordinates(location.toCoordinateString())
            } else {
                locationError = "Couldn't get a location fix - check that location is turned on."
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            fetchCurrentLocation()
        } else {
            locationError = "Location permission is needed to fill this in automatically."
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = DateroemAccent,
        unfocusedBorderColor = DateroemTextMuted.copy(alpha = 0.4f)
    )

    Box(modifier = Modifier.fillMaxSize().background(DateroemBackground)) {
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp)
        ) {
            Text(
                if (isEditing) "Edit Tree Registration" else "Register Trees",
                color = DateroemText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "One household's registered trees for this trip - Products can then be linked to this registration.",
                color = DateroemTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            VoiceAssistantToggle(voice, modifier = Modifier.padding(bottom = 8.dp))

            VoiceOutlinedTextField(
                value = state.houseHold,
                onValueChange = viewModel::updateHouseHold,
                assistant = voice,
                voiceKey = "household",
                label = { Text("Household") },
                placeholder = { Text("Household name") },
                singleLine = true,
                modifier = Modifier.padding(top = 4.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                OutlinedTextField(
                    value = state.coordinates,
                    onValueChange = viewModel::updateCoordinates,
                    label = { Text("Coordinates (optional)") },
                    placeholder = { Text("lat,long") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = {
                        locationPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    },
                    enabled = !locatingCoordinates,
                    modifier = Modifier.size(72.dp)
                ) {
                    if (locatingCoordinates) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp), color = DateroemAccent, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Filled.Place,
                            contentDescription = "Use current location",
                            tint = DateroemAccent,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            Text(
                "Tap the pin to fill this in from the device's current location - works offline too.",
                color = DateroemTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (locationError != null) {
                Text(locationError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            VoiceOutlinedTextField(
                value = state.noTrees,
                onValueChange = viewModel::updateNoTrees,
                assistant = voice,
                voiceKey = "noTrees",
                label = { Text("Number of trees *") },
                singleLine = true,
                numeric = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.padding(top = 12.dp)
            )
            VoiceOutlinedTextField(
                value = state.typeTrees,
                onValueChange = viewModel::updateTypeTrees,
                assistant = voice,
                voiceKey = "typeTrees",
                label = { Text("Tree type (e.g. Eucalyptus)") },
                singleLine = true,
                modifier = Modifier.padding(top = 12.dp)
            )

            Text("Status", color = DateroemTextMuted, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.active,
                    onClick = { viewModel.updateActive(true) },
                    label = { Text("Active") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DateroemAccent,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = !state.active,
                    onClick = { viewModel.updateActive(false) },
                    label = { Text("Inactive") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DateroemAccent,
                        selectedLabelColor = Color.White
                    )
                )
            }

            Text("Farmer", color = DateroemTextMuted, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !state.registeringNewFarmer,
                    onClick = { viewModel.setRegisteringNewFarmer(false) },
                    label = { Text("Existing farmer") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DateroemAccent,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = state.registeringNewFarmer,
                    onClick = { viewModel.setRegisteringNewFarmer(true) },
                    label = { Text("New farmer") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DateroemAccent,
                        selectedLabelColor = Color.White
                    )
                )
            }

            if (state.registeringNewFarmer) {
                VoiceOutlinedTextField(
                    value = state.newFarmerName,
                    onValueChange = viewModel::updateNewFarmerName,
                    assistant = voice,
                    voiceKey = "newFarmerName",
                    label = { Text("New farmer's name *") },
                    singleLine = true,
                    modifier = Modifier.padding(top = 10.dp)
                )
                VoiceOutlinedTextField(
                    value = state.newFarmerPhone,
                    onValueChange = viewModel::updateNewFarmerPhone,
                    assistant = voice,
                    voiceKey = "newFarmerPhone",
                    label = { Text("Phone (optional)") },
                    singleLine = true,
                    modifier = Modifier.padding(top = 10.dp)
                )
            } else {
                FarmerPickerField(
                    label = "Select farmer",
                    farmers = state.farmers,
                    selectedFarmerId = state.selectedFarmerId,
                    onSelect = viewModel::selectFarmer,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            Text(
                "Carried forward from a remainder",
                color = DateroemTextMuted,
                modifier = Modifier.padding(top = 20.dp, bottom = 6.dp)
            )
            DropdownField(
                label = "Remainder trees",
                selectedLabel = state.remainderTrees.firstOrNull { it.remainderTreesId == state.selectedRemainderTreesId }
                    ?.let { "#${it.remainderTreesId} - ${it.noTrees ?: 0} ${it.typeTrees ?: ""} (from trip ${it.previousTripId})" }
                    ?: "(none - fresh registration)",
                options = listOf<RemainderTree?>(null) + state.remainderTrees,
                optionLabel = {
                    it?.let { r -> "#${r.remainderTreesId} - ${r.noTrees ?: 0} ${r.typeTrees ?: ""} (from trip ${r.previousTripId})" }
                        ?: "(none - fresh registration)"
                },
                onSelect = { viewModel.selectRemainderTrees(it?.remainderTreesId) }
            )

            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                WoodButton(
                    onClick = { viewModel.submit() },
                    enabled = !state.submitting,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = WoodOn)
                    } else {
                        Text(if (isEditing) "Save Changes" else "Register Trees", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (isEditing) {
                // Same reasoning as ProductFormScreen/CampaignFormScreen's
                // own copy of this fix: the trip-selector FAB floats at a
                // fixed bottom-center position regardless of scroll, so
                // more gap above separates this from Save/Cancel.
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = !state.deleting,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                ) {
                    if (state.deleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.error, strokeWidth = 2.dp)
                    } else {
                        Text("Delete Tree Registration", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            // "add space in create tree between the trip selector area and
            // the cancel and register trees" - this spacer used to only
            // exist inside the isEditing branch above, so a fresh (non-
            // editing) registration had nothing clearing the floating
            // trip-selector FAB's footprint right under Cancel/Register.
            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}

@Composable
private fun <T> DropdownField(
    label: String,
    selectedLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .background(DateroemSurface, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Column {
                Text(label, color = DateroemTextMuted, style = MaterialTheme.typography.labelSmall)
                Text(selectedLabel, color = DateroemText)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
