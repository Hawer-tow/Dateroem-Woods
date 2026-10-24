package com.dateroem.woodsales.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.ui.common.DateField
import com.dateroem.woodsales.ui.common.EntityPickerField
import com.dateroem.woodsales.ui.common.TreePickerField
import com.dateroem.woodsales.ui.common.SpeakErrorEffect
import com.dateroem.woodsales.ui.common.VoiceAssistantToggle
import com.dateroem.woodsales.ui.common.VoiceOutlinedTextField
import com.dateroem.woodsales.ui.common.rememberVoiceAssistant
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodButton
import com.dateroem.woodsales.ui.theme.WoodOn
import com.dateroem.woodsales.ui.common.ErrorBox

@Composable
fun ExpenseFormScreen(
    viewModel: ExpenseFormViewModel,
    category: ExpenseCategory,
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val voice = rememberVoiceAssistant()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    SpeakErrorEffect(voice, state.error)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DateroemBackground)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp)
    ) {
        Text(
            "New ${category.label} entry",
            color = DateroemText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            "This is recorded but not yet validated - an admin matches it to the real payment later.",
            color = DateroemTextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        VoiceAssistantToggle(voice, modifier = Modifier.padding(bottom = 8.dp))

        VoiceOutlinedTextField(
            value = state.amount,
            onValueChange = viewModel::updateAmount,
            assistant = voice,
            voiceKey = "amount",
            label = { Text("Amount (KES) *") },
            singleLine = true,
            numeric = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.padding(top = 4.dp)
        )
        VoiceOutlinedTextField(
            value = state.description,
            onValueChange = viewModel::updateDescription,
            assistant = voice,
            voiceKey = "description",
            label = { Text("Description") },
            modifier = Modifier.padding(top = 12.dp)
        )

        if (category.hasPaymentMode) {
            Text("Paid with", color = DateroemTextMuted, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.type == "SOFT",
                    onClick = { viewModel.updateType("SOFT") },
                    label = { Text("Soft cash (M-Pesa/mobile)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DateroemAccent,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = state.type == "HARD",
                    onClick = { viewModel.updateType("HARD") },
                    label = { Text("Hard cash (physical)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DateroemAccent,
                        selectedLabelColor = Color.White
                    )
                )
            }
            Text(
                if (state.type == "HARD")
                    "Recorded as physical cash - the system opens a company reference number for it right away."
                else
                    "Recorded as mobile money - an admin will match it to the real M-Pesa payment.",
                color = DateroemTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (category == ExpenseCategory.TRANSPORT) {
            Text("Vehicle", color = DateroemTextMuted, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !state.vehicleNotInList,
                    onClick = { viewModel.setVehicleNotInList(false) },
                    label = { Text("Select from list") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DateroemAccent,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = state.vehicleNotInList,
                    onClick = { viewModel.setVehicleNotInList(true) },
                    label = { Text("Not in the list") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DateroemAccent,
                        selectedLabelColor = Color.White
                    )
                )
            }
            if (state.vehicleNotInList) {
                VoiceOutlinedTextField(
                    value = state.additionalId,
                    onValueChange = viewModel::updateAdditionalId,
                    assistant = voice,
                    voiceKey = "additionalId",
                    label = { Text("Number plate *") },
                    singleLine = true,
                    modifier = Modifier.padding(top = 10.dp)
                )
            } else {
                EntityPickerField(
                    label = "Vehicle *",
                    items = state.vehicles,
                    selectedId = state.selectedVehicleId,
                    itemId = { it.vehicleId },
                    itemTitle = { it.uniqueIdentifier ?: "Unnamed" },
                    itemSubtitle = { it.driver },
                    onSelect = viewModel::selectVehicle,
                    allowNone = false,
                    noneLabel = "Select vehicle",
                    dialogTitle = "Select Vehicle",
                    searchHint = "Search by plate or driver",
                    modifier = Modifier.padding(top = 10.dp)
                )
                if (state.vehicles.isEmpty()) {
                    Text(
                        "No vehicles registered yet - add one from the desktop app, or mark this as not in the list.",
                        color = DateroemTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        if (category == ExpenseCategory.MACHINE_EXPENSES) {
            EntityPickerField(
                label = "Machine *",
                items = state.machines,
                selectedId = state.selectedMachineId,
                itemId = { it.machineId },
                itemTitle = { it.name ?: "Unnamed" },
                itemSubtitle = { it.serialNo },
                onSelect = viewModel::selectMachine,
                allowNone = false,
                noneLabel = "Select machine",
                dialogTitle = "Select Machine",
                searchHint = "Search by name or serial number",
                modifier = Modifier.padding(top = 12.dp)
            )
            if (state.machines.isEmpty()) {
                Text(
                    "No machines registered yet - add one from the desktop app.",
                    color = DateroemTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        if (category == ExpenseCategory.FARMER_PAYMENTS) {
            // "im seeing the select farmer no farmer there ... connect it
            // to the trees instead which will connect to the farmer" -
            // tree-first: picking the household resolves its farmer
            // automatically (every tree has one - TreeFormViewModel
            // requires it to submit), so there's no separate farmer list
            // that can come up empty.
            TreePickerField(
                label = "Tree registration (household) *",
                trees = state.trees,
                farmers = state.farmers,
                selectedTreesId = state.selectedTreesId,
                onSelect = viewModel::selectTree,
                allowNone = false,
                noneLabel = "Select a tree registration",
                modifier = Modifier.padding(top = 12.dp)
            )
            if (state.trees.isEmpty()) {
                Text(
                    "No trees registered on this trip yet - register one from the Trees section first.",
                    color = DateroemTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            state.farmers.firstOrNull { it.farmerId == state.selectedFarmerId }?.let { farmer ->
                Text(
                    "Farmer: ${farmer.name ?: "Unnamed"}" + (farmer.phoneNo?.takeIf { it.isNotBlank() }?.let { " - $it" } ?: ""),
                    color = DateroemTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            VoiceOutlinedTextField(
                value = state.method,
                onValueChange = viewModel::updateMethod,
                assistant = voice,
                voiceKey = "method",
                label = { Text("Method (e.g. M-Pesa, Cash, Bank)") },
                singleLine = true,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        if (category == ExpenseCategory.PERMITS) {
            VoiceOutlinedTextField(
                value = state.serialNo,
                onValueChange = viewModel::updateSerialNo,
                assistant = voice,
                voiceKey = "serialNo",
                label = { Text("Serial number") },
                singleLine = true,
                modifier = Modifier.padding(top = 12.dp)
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField(
                    label = "Start",
                    value = state.startDate,
                    onValueChange = viewModel::updateStartDate,
                    modifier = Modifier.weight(1f)
                )
                DateField(
                    label = "End",
                    value = state.endDate,
                    onValueChange = viewModel::updateEndDate,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // "connect major expense to a user not just who created it ...
        // who its meant for" - universal across every category that
        // applies to (farmer-payments/permits have their own fields
        // instead, and are excluded from FOR_USER_CATEGORIES).
        //
        // "in machine dont write who this was for write who was incharge
        // of the machine at the time ... is not optional but compulsary" -
        // MACHINE_EXPENSES gets its own label and is required; every other
        // category in FOR_USER_CATEGORIES keeps the original optional
        // "who this was for" wording.
        if (category in FOR_USER_CATEGORIES) {
            val machine = category == ExpenseCategory.MACHINE_EXPENSES
            EntityPickerField(
                label = if (machine) "Who was in charge of the machine at the time *" else "Who this was for (optional)",
                items = state.forUsers,
                selectedId = state.selectedForUserId,
                itemId = { it.userId },
                itemTitle = { it.name?.takeIf { n -> n.isNotBlank() } ?: "User #${it.userId}" },
                itemSubtitle = { null },
                onSelect = viewModel::selectForUser,
                allowNone = !machine,
                noneLabel = "Not tied to a specific person",
                dialogTitle = if (machine) "Select who was in charge" else "Select user",
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        if (state.error != null) {
            ErrorBox(state.error!!, modifier = Modifier.padding(top = 12.dp))
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
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
