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
import com.dateroem.woodsales.data.model.Tree
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted

// A search-when-it's-long picker for Tree, shown as "household - farmer's
// phone number" - "one selects the present tree form the e.g the house
// hoold name, the phone number and then that is added in the products
// treeid column so we know where the product came from". Shared with the
// remainder-trees origin picker, since both need the exact same
// household+farmer lookup.
private const val SEARCH_THRESHOLD = 15

fun treePickerLabel(tree: Tree, farmers: List<Farmer>): String {
    val farmer = farmers.firstOrNull { it.farmerId == tree.farmerId }
    val phone = farmer?.phoneNo?.takeIf { it.isNotBlank() }
    return if (phone != null) "${tree.houseHold ?: "Household"} - $phone" else (tree.houseHold ?: "Household")
}

@Composable
fun TreePickerField(
    label: String,
    trees: List<Tree>,
    farmers: List<Farmer>,
    selectedTreesId: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    allowNone: Boolean = true,
    noneLabel: String = "(none)"
) {
    var showPicker by remember { mutableStateOf(false) }
    val selected = trees.firstOrNull { it.treesId == selectedTreesId }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showPicker = true }
            .background(DateroemSurface, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Column {
            Text(label, color = DateroemTextMuted, style = MaterialTheme.typography.labelSmall)
            Text(selected?.let { treePickerLabel(it, farmers) } ?: noneLabel, color = DateroemText)
        }
    }

    if (showPicker) {
        TreePickerDialog(
            trees = trees,
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
private fun TreePickerDialog(
    trees: List<Tree>,
    farmers: List<Farmer>,
    allowNone: Boolean,
    noneLabel: String,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(trees, farmers, query) {
        if (query.isBlank()) {
            trees
        } else {
            trees.filter { tree ->
                val farmer = farmers.firstOrNull { it.farmerId == tree.farmerId }
                (tree.houseHold?.contains(query, ignoreCase = true) == true) ||
                    (farmer?.phoneNo?.contains(query) == true) ||
                    (farmer?.name?.contains(query, ignoreCase = true) == true)
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
                Text("Select Tree", color = DateroemText, style = MaterialTheme.typography.titleMedium)

                if (trees.size > SEARCH_THRESHOLD) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search by household or phone") },
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
                            TreeRow(name = noneLabel, subtitle = null, onClick = { onSelect(null) })
                        }
                    }
                    items(filtered) { tree ->
                        TreeRow(
                            name = treePickerLabel(tree, farmers),
                            subtitle = "${tree.noTrees ?: 0} ${tree.typeTrees ?: "trees"}",
                            onClick = { onSelect(tree.treesId) }
                        )
                    }
                    if (filtered.isEmpty() && query.isNotBlank()) {
                        item {
                            Text(
                                "No trees match \"$query\".",
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
private fun TreeRow(name: String, subtitle: String?, onClick: () -> Unit) {
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
