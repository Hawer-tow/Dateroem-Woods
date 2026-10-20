package com.dateroem.woodsales.ui.trees

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.data.model.Farmer
import com.dateroem.woodsales.data.model.RemainderTree
import com.dateroem.woodsales.data.model.Tree
import com.dateroem.woodsales.ui.common.SpeakErrorEffect
import com.dateroem.woodsales.ui.common.TreePickerField
import com.dateroem.woodsales.ui.common.VoiceAssistantToggle
import com.dateroem.woodsales.ui.common.VoiceOutlinedTextField
import com.dateroem.woodsales.ui.common.rememberVoiceAssistant
import com.dateroem.woodsales.ui.common.treePickerLabel
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodButton
import com.dateroem.woodsales.ui.theme.WoodOn

@Composable
fun RemainderTreeFormScreen(viewModel: RemainderTreeFormViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val voice = rememberVoiceAssistant()
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
                .padding(16.dp)
        ) {
            Text(
                "Finalize Trees",
                color = DateroemText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "If this trip ends with unused registered trees, record them here - a later trip can then pick them up instead of starting a fresh registration. You can add as many as you need; this stays open until you're done finalizing.",
                color = DateroemTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (state.recorded.isNotEmpty()) {
                Text("Recorded so far", color = DateroemText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                state.recorded.forEach { remainder ->
                    RecordedRemainderRow(remainder, state.trees, state.farmers)
                }
                Text("Add another", color = DateroemText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            }

            TreePickerField(
                label = "Origin tree (household - phone)",
                trees = state.trees,
                farmers = state.farmers,
                selectedTreesId = state.selectedOriginTreesId,
                onSelect = viewModel::selectOriginTree,
                allowNone = false,
                noneLabel = "Select tree"
            )
            if (state.trees.isEmpty()) {
                Text(
                    "No trees registered for this trip yet.",
                    color = DateroemTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            VoiceAssistantToggle(voice, modifier = Modifier.padding(top = 12.dp))

            VoiceOutlinedTextField(
                value = state.noTrees,
                onValueChange = viewModel::updateNoTrees,
                assistant = voice,
                voiceKey = "noTrees",
                label = { Text("Number of remainder trees *") },
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
            VoiceOutlinedTextField(
                value = state.totalBodyFeet,
                onValueChange = viewModel::updateTotalBodyFeet,
                assistant = voice,
                voiceKey = "totalBodyFeet",
                label = { Text("Total body feet (optional)") },
                singleLine = true,
                numeric = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.padding(top = 12.dp)
            )

            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                    Text("Done")
                }
                WoodButton(
                    onClick = { viewModel.addRemainder() },
                    enabled = !state.submitting,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(modifier = Modifier.padding(2.dp), color = WoodOn)
                    } else {
                        Text("Add Remainder", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordedRemainderRow(remainder: RemainderTree, trees: List<Tree>, farmers: List<Farmer>) {
    val originTree = trees.firstOrNull { it.treesId == remainder.originTreesId }
    val originLabel = originTree?.let { treePickerLabel(it, farmers) }

    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("${remainder.noTrees ?: 0} ${remainder.typeTrees ?: "trees"}", color = DateroemAccent)
            if (originLabel != null) {
                Text("From: $originLabel", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
