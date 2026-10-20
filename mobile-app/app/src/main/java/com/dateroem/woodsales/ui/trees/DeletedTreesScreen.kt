package com.dateroem.woodsales.ui.trees

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import com.dateroem.woodsales.data.model.Tree
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodButton
import com.dateroem.woodsales.ui.theme.WoodOn

// "delete trees should also be created and add a bin just like the
// product section does it" - same recoverable-bin pattern as
// DeletedProductsScreen.
@Composable
fun DeletedTreesScreen(viewModel: DeletedTreesViewModel) {
    val state by viewModel.uiState.collectAsState()
    val restoringIds by viewModel.restoringIds.collectAsState()
    val restoreError by viewModel.restoreError.collectAsState()

    restoreError?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissRestoreError() },
            title = { Text("Couldn't restore this tree registration") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissRestoreError() }) { Text("OK") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        when (val current = state) {
            is DeletedTreesUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
            }
            is DeletedTreesUiState.Error -> {
                Column(modifier = Modifier.align(Alignment.Center)) {
                    Text("Couldn't load deleted trees", color = MaterialTheme.colorScheme.error)
                    Text(current.message, color = DateroemTextMuted, modifier = Modifier.padding(top = 4.dp))
                    Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Retry")
                    }
                }
            }
            is DeletedTreesUiState.Loaded -> {
                if (current.trees.isEmpty()) {
                    Text(
                        "Nothing deleted right now.",
                        color = DateroemTextMuted,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn {
                        items(current.trees) { tree ->
                            DeletedTreeRow(
                                tree,
                                restoring = tree.treesId in restoringIds,
                                onRestore = { viewModel.restore(tree.treesId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeletedTreeRow(tree: Tree, restoring: Boolean, onRestore: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tree.houseHold?.takeIf { it.isNotBlank() } ?: "Trees #${tree.treesId}", color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "${tree.noTrees ?: 0} ${tree.typeTrees ?: "trees"}",
                    color = DateroemTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            WoodButton(
                onClick = onRestore,
                enabled = !restoring,
            ) {
                if (restoring) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = WoodOn)
                } else {
                    Text("Restore")
                }
            }
        }
    }
}
