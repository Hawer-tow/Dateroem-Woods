package com.dateroem.woodsales.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dateroem.woodsales.data.model.Product
import com.dateroem.woodsales.ui.common.ProductThumbnail
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemAccentHover
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodButton
import com.dateroem.woodsales.ui.theme.WoodFab
import com.dateroem.woodsales.ui.common.ErrorBox

// A retired product's card: a muted wood-brown, like the desktop's retired
// rows, so it reads as "off the shelf" at a glance.
private val RetiredSurface = Color(0xFF3C2E1C)

@Composable
fun ProductsScreen(
    viewModel: ProductsViewModel,
    onAddProduct: () -> Unit,
    onEditProduct: (Int) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val depletedCount by viewModel.depletedCount.collectAsState()
    val showingOutOfStock by viewModel.showingOutOfStock.collectAsState()
    val busyIds by viewModel.busyIds.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    var confirming by remember { mutableStateOf<Product?>(null) }

    confirming?.let { product ->
        val retire = !product.retired
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(if (retire) "Retire this product?" else "Bring this product back?") },
            text = {
                Text(
                    if (retire) {
                        "'${product.displayName}' will be hidden from the shop and can't be ordered. " +
                            "Its stock and any existing orders are kept, and you can bring it back any time."
                    } else {
                        "'${product.displayName}' will be back in the shop, and customers can order it again."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setRetired(product, retire)
                    confirming = null
                }) { Text(if (retire) "Retire" else "Bring back", color = DateroemAccent) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text("Cancel", color = DateroemTextMuted) }
            }
        )
    }

    actionError?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissActionError() },
            title = { Text("Couldn't update this product") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { viewModel.dismissActionError() }) { Text("OK") } }
        )
    }

    Scaffold(
        containerColor = DateroemBackground,
        floatingActionButton = {
            WoodFab(onClick = onAddProduct) {
                Icon(Icons.Filled.Add, contentDescription = "Add product")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // "the admins are notified" - only while something has run out
            // (or while that list is open, as the way back).
            if (viewModel.isAdmin && (depletedCount > 0 || showingOutOfStock)) {
                NeedsAttentionBanner(
                    count = depletedCount,
                    showingOutOfStock = showingOutOfStock,
                    onClick = { viewModel.toggleOutOfStock() }
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when (val current = state) {
                    is ProductsUiState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = DateroemAccent
                        )
                    }
                    is ProductsUiState.Error -> {
                        Column(modifier = Modifier.align(Alignment.Center)) {
                            Text("Couldn't load products", color = MaterialTheme.colorScheme.error)
                            ErrorBox(current.message, modifier = Modifier.padding(top = 8.dp))
                            Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                                Text("Retry")
                            }
                        }
                    }
                    is ProductsUiState.Loaded -> {
                        if (current.products.isEmpty()) {
                            Text(
                                if (showingOutOfStock) "Nothing is out of stock right now." else "No products yet - tap + to create one.",
                                color = DateroemTextMuted,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn {
                                items(current.products, key = { it.productId }) { product ->
                                    ProductRow(
                                        product,
                                        canBringBack = viewModel.isAdmin,
                                        busy = product.productId in busyIds,
                                        onClick = { onEditProduct(product.productId) },
                                        onBringBack = { confirming = product }
                                    )
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
private fun NeedsAttentionBanner(count: Int, showingOutOfStock: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        if (showingOutOfStock) {
            "←  Back to all products"
        } else {
            "Needs attention: $count product${if (count != 1) "s" else ""} out of stock — tap to review"
        },
        color = DateroemAccentHover,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(DateroemAccent.copy(alpha = 0.16f), shape)
            .border(1.dp, DateroemAccent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    )
}

@Composable
// Retiring lives inside the product (its edit screen) so it can't be hit by
// accident from the list; only a retired product shows a button here,
// Bring back.
private fun ProductRow(
    product: Product,
    canBringBack: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    onBringBack: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (product.retired) RetiredSurface else DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductThumbnail(
                imageUrl = product.image,
                modifier = Modifier.size(72.dp).alpha(if (product.retired) 0.6f else 1f)
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(product.displayName, color = MaterialTheme.colorScheme.onSurface)
                if (product.retired) {
                    // "Retired" chip - off the shelf, but kept and reversible.
                    Text(
                        "Retired · hidden from the shop",
                        color = DateroemAccentHover,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 4.dp, bottom = 2.dp)
                            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                // The badge sits on its own line under the name: beside a long
                // name ("Grevillea table/stool templates") it used to be squeezed
                // into a column one letter wide. One line, ending in "..." if a
                // note is longer than the card.
                if (!product.marketerNote.isNullOrBlank()) {
                    Text(
                        product.marketerNote,
                        color = DateroemAccent,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 4.dp, bottom = 2.dp)
                            .background(DateroemAccent.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    "${product.type ?: ""} ${product.sku?.let { "- $it" } ?: ""}",
                    color = DateroemTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                val unitLabel = if (product.unit == "board_feet") "board ft" else "in stock"
                Text(
                    "KES ${product.price ?: 0.0} - ${product.stock ?: 0.0} $unitLabel",
                    color = DateroemAccent,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (canBringBack && product.retired) {
                Spacer(modifier = Modifier.size(8.dp))
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = DateroemAccent, strokeWidth = 2.dp)
                } else {
                    // Bringing it back is the positive action: painted wood.
                    WoodButton(onClick = onBringBack) { Text("Bring back") }
                }
            }
        }
    }
}
