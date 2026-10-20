package com.dateroem.woodsales.ui.products

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.dateroem.woodsales.ui.common.ProductThumbnail
import com.dateroem.woodsales.ui.common.SpeakErrorEffect
import com.dateroem.woodsales.ui.common.TreePickerField
import com.dateroem.woodsales.ui.common.VoiceAssistantToggle
import com.dateroem.woodsales.ui.common.VoiceOutlinedTextField
import com.dateroem.woodsales.ui.common.defaultForest
import com.dateroem.woodsales.ui.common.drawPineTree
import com.dateroem.woodsales.ui.common.rememberVoiceAssistant
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSheetEnd
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import java.io.File
import com.dateroem.woodsales.ui.theme.WoodButton
import com.dateroem.woodsales.ui.theme.WoodOn

@Composable
fun ProductFormScreen(
    viewModel: ProductFormViewModel,
    isEditing: Boolean,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    onCancel: () -> Unit,
    // After retiring, back to the list, where it now shows as retired
    // with Bring back.
    onRetired: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showPickerChoice by remember { mutableStateOf(false) }
    // Saveable, as in the campaign form: turning the phone while taking
    // the photo rebuilds this screen, and plain remember lost the file.
    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRetireConfirm by remember { mutableStateOf(false) }
    val voice = rememberVoiceAssistant()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    LaunchedEffect(state.deleted) {
        if (state.deleted) onDeleted()
    }
    LaunchedEffect(state.retiredNow) {
        if (state.retiredNow) onRetired()
    }

    if (showRetireConfirm) {
        AlertDialog(
            onDismissRequest = { showRetireConfirm = false },
            title = { Text("Retire this product?") },
            text = {
                Text(
                    "It will be hidden from the shop and can't be ordered. Its stock and any existing orders " +
                        "are kept, and you can bring it back any time from the product list."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRetireConfirm = false
                    viewModel.retire()
                }) { Text("Retire", color = DateroemAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showRetireConfirm = false }) { Text("Cancel", color = DateroemTextMuted) }
            }
        )
    }
    SpeakErrorEffect(voice, state.error)

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this product?") },
            text = { Text("It'll disappear from the products list, but stays recoverable - an admin can restore it from Deleted Products.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingCameraUri?.let { viewModel.pickImage(it) }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = createCameraCaptureUri(context)
            pendingCameraUri = uri
            takePicture.launch(uri)
        }
    }
    val pickFromGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.pickImage(uri)
    }

    if (showPickerChoice) {
        AlertDialog(
            onDismissRequest = { showPickerChoice = false },
            title = { Text("Add a photo") },
            text = { Text("No worries if you skip this - you can always add or change the picture later.") },
            confirmButton = {
                TextButton(onClick = {
                    showPickerChoice = false
                    cameraPermission.launch(android.Manifest.permission.CAMERA)
                }) { Text("Take Photo") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPickerChoice = false
                    pickFromGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Choose from Gallery") }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(DateroemBackground, DateroemSheetEnd)))
    ) {
        // Faint forest silhouette along the bottom, low-alpha so it reads
        // as ambience behind the form rather than competing with it.
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp).align(Alignment.BottomCenter)) {
            defaultForest.forEach { tree ->
                drawPineTree(tree.xFraction, tree.scale * 0.8f, growth = 1f, tone = tree.tone.copy(alpha = 0.35f), canvasSize = size)
            }
        }

        if (state.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DateroemAccent)
            }
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
                if (isEditing) "Edit Product" else "New Product",
                color = DateroemText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            if (state.retired) {
                Text(
                    "Retired · hidden from the shop. Bring it back from the product list.",
                    color = DateroemAccent,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .background(DateroemAccent.copy(alpha = 0.14f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            ProductThumbnail(
                imageUrl = (state.pickedImageUri?.toString() ?: state.existingImageUrl),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clickable { showPickerChoice = true },
                cornerRadius = 12.dp
            )
            if (state.pickedImageUri == null && state.existingImageUrl == null) {
                Text(
                    "Tap the photo area to add a picture (optional - add it any time)",
                    color = DateroemTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }


            VoiceAssistantToggle(voice, modifier = Modifier.padding(top = 12.dp))

            VoiceOutlinedTextField(
                value = state.type,
                onValueChange = viewModel::updateType,
                assistant = voice,
                voiceKey = "type",
                label = { Text("Tree type *") },
                singleLine = true,
                modifier = Modifier.padding(top = 16.dp)
            )
            VoiceOutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                assistant = voice,
                voiceKey = "name",
                label = { Text("Product name *") },
                singleLine = true,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                "Required - the uploaded photo is named after this.",
                color = DateroemTextMuted,
                style = MaterialTheme.typography.bodySmall,
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
            VoiceOutlinedTextField(
                value = state.price,
                onValueChange = viewModel::updatePrice,
                assistant = voice,
                voiceKey = "price",
                label = { Text("Price (KES) *") },
                singleLine = true,
                numeric = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.padding(top = 12.dp)
            )

            Text("Measured as", color = DateroemTextMuted, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProductUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = state.unit == unit,
                        onClick = { viewModel.updateUnit(unit) },
                        label = { Text(unit.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DateroemAccent,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            VoiceOutlinedTextField(
                value = state.quantity,
                onValueChange = viewModel::updateQuantity,
                assistant = voice,
                voiceKey = "quantity",
                label = { Text(if (state.unit == ProductUnit.PIECES) "Stock (pieces) *" else "Total board feet *") },
                singleLine = true,
                numeric = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.padding(top = 12.dp)
            )

            VoiceOutlinedTextField(
                value = state.marketerNote,
                onValueChange = viewModel::updateMarketerNote,
                assistant = voice,
                voiceKey = "marketerNote",
                label = { Text("Promo tag (optional)") },
                singleLine = true,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                "e.g. \"Most Ordered\" or \"Only 2 left\" - shows as a badge right on this product's card.",
                color = DateroemTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )

            // "one selects the present tree form the e.g the house hoold
            // name, the phone number and then that is added in the
            // products treeid column so we know where the product came
            // from" - scoped to whichever trip is active right now.
            if (state.trees.isNotEmpty()) {
                Text("Linked tree registration", color = DateroemTextMuted, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                TreePickerField(
                    label = "Household - phone",
                    trees = state.trees,
                    farmers = state.farmers,
                    selectedTreesId = state.treesId,
                    onSelect = viewModel::selectTree
                )
            } else {
                Text(
                    "No trees registered for the active trip yet - register some from the Trees section to link one here.",
                    color = DateroemTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // "space between create products and cancel" - a plain
                // TextButton has no visible boundary, so next to the solid
                // Create/Save button it read as an awkward gap even with
                // even weights and real spacing between them. An outlined
                // button gives it the same visual footprint.
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                WoodButton(
                    onClick = { viewModel.submit(context) },
                    enabled = !state.submitting,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = WoodOn)
                    } else {
                        Text(if (isEditing) "Save Changes" else "Create Product", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (isEditing) {
                // "the delete button behind the trip selector button at
                // the bottom of the screen ... preventing it from being
                // too close to the rest of the fields for accessibility
                // and risk prevention" - the trip-selector FAB floats at
                // a fixed bottom-center position (Scaffold's own
                // FabPosition.Center) regardless of scroll, so a
                // full-width destructive button sitting flush at the end
                // of the scrollable content always ends up hidden right
                // behind it. More gap above separates it from Save/
                // Cancel (harder to fat-finger by mistake); the spacer
                // below clears the FAB's own 52dp + margin footprint so
                // there's real daylight between them even scrolled all
                // the way down.
                //
                // "can the retire option be inside the product itself" -
                // here, with the other careful actions, well away from
                // Save, and only for admins on a product still on sale.
                if (viewModel.isAdmin && !state.retired) {
                    OutlinedButton(
                        onClick = { showRetireConfirm = true },
                        enabled = !state.retiring,
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                    ) {
                        if (state.retiring) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DateroemAccent, strokeWidth = 2.dp)
                        } else {
                            Text("Retire Product", color = DateroemAccent)
                        }
                    }
                    Text(
                        "Takes it off the shop without deleting it. Stock and orders are kept.",
                        color = DateroemTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                    )
                }
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = !state.deleting,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                ) {
                    if (state.deleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.error, strokeWidth = 2.dp)
                    } else {
                        Text("Delete Product", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
}

private fun createCameraCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
