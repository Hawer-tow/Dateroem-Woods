package com.dateroem.woodsales.ui.trips

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.ui.common.DateField
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
fun TripFormScreen(viewModel: TripFormViewModel, onSaved: () -> Unit, onCancel: () -> Unit, onOpenTrip: (Int) -> Unit = {}) {
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
        Text("New Trip", color = DateroemText, fontSize = 20.sp, fontWeight = FontWeight.Bold)

        val openTripId = state.openTripId
        if (state.checking) {
            CircularProgressIndicator(color = DateroemAccent, modifier = Modifier.padding(top = 24.dp))
            return@Column
        }
        if (openTripId != null) {
            Text(
                "Trip #$openTripId is still open.",
                color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                "Before a new trip can start, every order on it must be delivered or dropped to the next trip, its loans " +
                    "settled and its profit recorded. All of that happens when you Finalize Trip.",
                color = DateroemTextMuted, modifier = Modifier.padding(top = 6.dp)
            )
            WoodButton(
                onClick = { onOpenTrip(openTripId) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) { Text("Open Trip #$openTripId to finalize it", fontWeight = FontWeight.Bold) }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            return@Column
        }
        Text(
            "Starts the trip that everything else - deliveries, expenses, loans, finance - gets attached to.",
            color = DateroemTextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        VoiceAssistantToggle(voice, modifier = Modifier.padding(bottom = 8.dp))

        DateField(
            label = "Start date *",
            value = state.startDate,
            onValueChange = viewModel::updateStartDate,
            modifier = Modifier.fillMaxWidth()
        )
        VoiceOutlinedTextField(
            value = state.productDescription,
            onValueChange = viewModel::updateProductDescription,
            assistant = voice,
            voiceKey = "productDescription",
            label = { Text("Product description") },
            modifier = Modifier.padding(top = 12.dp)
        )

        if (state.error != null) {
            ErrorBox(state.error!!, modifier = Modifier.padding(top = 12.dp))
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
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
                    Text("Create Trip", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
