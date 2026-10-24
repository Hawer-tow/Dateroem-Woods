package com.dateroem.woodsales.ui.marketing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.dateroem.woodsales.data.model.Product
import com.dateroem.woodsales.ui.common.ProductThumbnail
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodButton
import com.dateroem.woodsales.ui.theme.WoodOn
import java.util.Locale
import com.dateroem.woodsales.ui.common.ErrorBox

private const val SEARCH_THRESHOLD = 8

// "Products in this campaign" - which products the campaign promotes. The
// website's Featured page shows a campaign (and tags these products) only
// once it has at least one, so customers always have something to buy.
// Same tap-to-open card as EntityPickerField, but several can be ticked.
@Composable
fun CampaignProductsField(
    products: List<Product>,
    loaded: Boolean,
    error: String?,
    selectedIds: Set<Int>,
    onChange: (Set<Int>) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val chosen = products.filter { it.productId in selectedIds }

    Column(modifier = modifier.fillMaxWidth()) {
        Text("Products in this campaign", color = DateroemTextMuted, modifier = Modifier.padding(bottom = 6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DateroemSurface)
                .clickable(enabled = loaded) { showPicker = true }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            when {
                error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    ErrorBox(error, modifier = Modifier.weight(1f), small = true)
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
                !loaded -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = DateroemAccent)
                    Text("Loading products...", color = DateroemTextMuted, modifier = Modifier.padding(start = 10.dp))
                }
                chosen.isEmpty() -> Text("None chosen yet - tap to choose", color = DateroemTextMuted)
                else -> Column {
                    Text(
                        if (chosen.size == 1) "1 product - tap to change" else "${chosen.size} products - tap to change",
                        color = DateroemText,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        chosen.joinToString(", ") { it.displayName },
                        color = DateroemTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }

    if (showPicker) {
        CampaignProductsDialog(
            products = products,
            initial = selectedIds,
            onDone = {
                onChange(it)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

// Ticks only take effect on Done; Cancel (or tapping outside) leaves the
// campaign's products as they were.
@Composable
private fun CampaignProductsDialog(
    products: List<Product>,
    initial: Set<Int>,
    onDone: (Set<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var picked by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    val shown = remember(products, query) {
        if (query.isBlank()) {
            products
        } else {
            products.filter {
                it.displayName.contains(query, ignoreCase = true) || it.type?.contains(query, ignoreCase = true) == true
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DateroemBackground),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Products in this campaign", color = DateroemText, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tick every product this campaign promotes.",
                    color = DateroemTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )

                if (products.size > SEARCH_THRESHOLD) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search products") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DateroemAccent,
                            unfocusedBorderColor = DateroemTextMuted.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    )
                }

                if (products.isEmpty()) {
                    Text(
                        "There are no products yet - add one in Products first.",
                        color = DateroemTextMuted,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }

                LazyColumn(modifier = Modifier.weight(1f, fill = false).padding(top = 8.dp)) {
                    items(shown, key = { it.productId }) { product ->
                        val checked = product.productId in picked
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .toggleable(value = checked, role = Role.Checkbox) { tick ->
                                    picked = if (tick) picked + product.productId else picked - product.productId
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = DateroemAccent,
                                    uncheckedColor = DateroemTextMuted,
                                    checkmarkColor = WoodOn
                                ),
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            ProductThumbnail(imageUrl = product.image, modifier = Modifier.size(44.dp))
                            Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                                Text(
                                    product.displayName,
                                    color = DateroemText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val details = listOfNotNull(
                                    product.type?.takeIf { it.isNotBlank() },
                                    product.price?.let { String.format(Locale.US, "KES %,.0f", it) }
                                ).joinToString(" - ")
                                if (details.isNotEmpty()) {
                                    Text(
                                        details,
                                        color = DateroemTextMuted,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    if (shown.isEmpty() && query.isNotBlank()) {
                        item {
                            Text(
                                "Nothing matches \"$query\".",
                                color = DateroemTextMuted,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    Text(
                        if (picked.isEmpty()) "None chosen" else "${picked.size} chosen",
                        color = DateroemTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    WoodButton(onClick = { onDone(picked) }) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
