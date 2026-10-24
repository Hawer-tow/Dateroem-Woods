package com.dateroem.woodsales.ui.trees

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dateroem.woodsales.data.model.Tree
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodFab
import com.dateroem.woodsales.ui.common.AppIcons
import com.dateroem.woodsales.ui.common.IconLabel
import com.dateroem.woodsales.ui.common.ErrorBox

@Composable
fun TreesScreen(
    viewModel: TreesViewModel,
    isAdmin: Boolean,
    onAdd: () -> Unit,
    onSelect: (Int) -> Unit,
    onFinalizeRemainders: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = DateroemBackground,
        floatingActionButton = {
            WoodFab(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Register trees")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when (val current = state) {
                is TreesUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
                }
                is TreesUiState.Error -> {
                    Column(modifier = Modifier.align(Alignment.Center)) {
                        Text("Couldn't load trees", color = MaterialTheme.colorScheme.error)
                        ErrorBox(current.message, modifier = Modifier.padding(top = 8.dp))
                        Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Retry")
                        }
                    }
                }
                is TreesUiState.Loaded -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // "allow a place where we tick there were
                        // remainders for admin when finalizing a trip" -
                        // admin-only, since this is a trip-finalization
                        // action, not day-to-day tree registration.
                        if (isAdmin) {
                            FinalizeRemaindersRow(onClick = onFinalizeRemainders)
                        }

                        if (current.trees.isEmpty()) {
                            Text(
                                "No trees registered for this trip yet - tap + to add the first household.",
                                color = DateroemTextMuted,
                                modifier = Modifier.padding(top = 24.dp)
                            )
                        } else {
                            LazyColumn {
                                items(current.trees) { tree ->
                                    TreeRow(tree, onClick = { onSelect(tree.treesId) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FinalizeRemaindersRow(onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            IconLabel(AppIcons.ClipboardCheck, "Finalize Trees", color = DateroemAccent, fontWeight = FontWeight.Bold, iconSize = 18.dp)
            Text(
                "Record any remainder trees left over from this trip",
                color = DateroemTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun TreeRow(tree: Tree, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tree.houseHold ?: "Unnamed household", color = DateroemText, fontWeight = FontWeight.Bold)
                Text(
                    "${tree.noTrees ?: 0} ${tree.typeTrees ?: "trees"}",
                    color = DateroemTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                if (tree.remainderTreesId != null) {
                    Text(
                        "Carried forward from remainder #${tree.remainderTreesId}",
                        color = DateroemAccent,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (tree.active == false) {
                Text(
                    "Inactive",
                    color = DateroemTextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
