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
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted

// "machine should be selected from the machine table" / "select the
// vehicle one used instead of using the vehicle id" - a generic version
// of FarmerPickerField's own "a proper scrollable list instead of typing
// an ID" pattern, reused for Machine and Vehicle instead of duplicating
// the whole dialog+search+row implementation a second and third time.
private const val SEARCH_THRESHOLD = 15

@Composable
fun <T> EntityPickerField(
    label: String,
    items: List<T>,
    selectedId: Int?,
    itemId: (T) -> Int,
    itemTitle: (T) -> String,
    itemSubtitle: (T) -> String?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    allowNone: Boolean = true,
    noneLabel: String = "(none)",
    dialogTitle: String = "Select",
    searchHint: String = "Search"
) {
    var showPicker by remember { mutableStateOf(false) }
    val selected = items.firstOrNull { itemId(it) == selectedId }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showPicker = true }
            .background(DateroemSurface, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Column {
            Text(label, color = DateroemTextMuted, style = MaterialTheme.typography.labelSmall)
            Text(selected?.let(itemTitle) ?: noneLabel, color = DateroemText)
        }
    }

    if (showPicker) {
        EntityPickerDialog(
            items = items,
            itemId = itemId,
            itemTitle = itemTitle,
            itemSubtitle = itemSubtitle,
            allowNone = allowNone,
            noneLabel = noneLabel,
            dialogTitle = dialogTitle,
            searchHint = searchHint,
            onSelect = {
                onSelect(it)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun <T> EntityPickerDialog(
    items: List<T>,
    itemId: (T) -> Int,
    itemTitle: (T) -> String,
    itemSubtitle: (T) -> String?,
    allowNone: Boolean,
    noneLabel: String,
    dialogTitle: String,
    searchHint: String,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(items, query) {
        if (query.isBlank()) {
            items
        } else {
            items.filter {
                itemTitle(it).contains(query, ignoreCase = true) ||
                    (itemSubtitle(it)?.contains(query, ignoreCase = true) == true)
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
                Text(dialogTitle, color = DateroemText, style = MaterialTheme.typography.titleMedium)

                if (items.size > SEARCH_THRESHOLD) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(searchHint) },
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
                            EntityRow(title = noneLabel, subtitle = null, onClick = { onSelect(null) })
                        }
                    }
                    items(filtered) { entry ->
                        EntityRow(
                            title = itemTitle(entry),
                            subtitle = itemSubtitle(entry),
                            onClick = { onSelect(itemId(entry)) }
                        )
                    }
                    if (filtered.isEmpty() && query.isNotBlank()) {
                        item {
                            Text(
                                "Nothing matches \"$query\".",
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
private fun EntityRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(title, color = DateroemText)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
