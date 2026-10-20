package com.dateroem.woodsales.ui.shipping

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun ShippingAreaFormScreen(
    viewModel: ShippingAreaFormViewModel,
    isEditing: Boolean,
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val voice = rememberVoiceAssistant()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    SpeakErrorEffect(voice, state.error)

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
                if (isEditing) "Edit Delivery Area" else "New Delivery Area",
                color = DateroemText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "Where customers pick up their order - this is the same list the website's checkout shows.",
                color = DateroemTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            VoiceAssistantToggle(voice, modifier = Modifier.padding(bottom = 8.dp))

            VoiceOutlinedTextField(
                value = state.city,
                onValueChange = viewModel::updateCity,
                assistant = voice,
                voiceKey = "city",
                label = { Text("City *") },
                singleLine = true,
                modifier = Modifier.padding(top = 4.dp)
            )
            VoiceOutlinedTextField(
                value = state.areaName,
                onValueChange = viewModel::updateAreaName,
                assistant = voice,
                voiceKey = "areaName",
                label = { Text("Area / pickup point name *") },
                singleLine = true,
                modifier = Modifier.padding(top = 12.dp)
            )
            VoiceOutlinedTextField(
                value = state.description,
                onValueChange = viewModel::updateDescription,
                assistant = voice,
                voiceKey = "description",
                label = { Text("Description (optional)") },
                modifier = Modifier.padding(top = 12.dp)
            )

            Text("Status", color = DateroemTextMuted, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.isActive,
                    onClick = { viewModel.updateIsActive(true) },
                    label = { Text("Active") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DateroemAccent,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = !state.isActive,
                    onClick = { viewModel.updateIsActive(false) },
                    label = { Text("Inactive") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DateroemAccent,
                        selectedLabelColor = Color.White
                    )
                )
            }
            Text(
                "Inactive areas stay saved here but disappear from the website's checkout options.",
                color = DateroemTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
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
                        Text(if (isEditing) "Save Changes" else "Create Area", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
