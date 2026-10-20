package com.dateroem.woodsales.ui.marketing

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.dateroem.woodsales.ui.common.DateField
import com.dateroem.woodsales.ui.common.ProductThumbnail
import com.dateroem.woodsales.ui.common.SpeakErrorEffect
import com.dateroem.woodsales.ui.common.VideoPlayer
import com.dateroem.woodsales.ui.common.VoiceAssistantToggle
import com.dateroem.woodsales.ui.common.VoiceOutlinedTextField
import com.dateroem.woodsales.ui.common.defaultForest
import com.dateroem.woodsales.ui.common.drawPineTree
import com.dateroem.woodsales.ui.common.rememberVoiceAssistant
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSheetEnd
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import java.io.File
import com.dateroem.woodsales.ui.theme.WoodButton
import com.dateroem.woodsales.ui.theme.WoodOn
import androidx.compose.material3.Icon
import com.dateroem.woodsales.ui.common.AppIcons

private enum class PendingCameraAction { PHOTO, VIDEO }

@Composable
fun CampaignFormScreen(
    viewModel: CampaignFormViewModel,
    isEditing: Boolean,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    onCancel: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showPickerChoice by remember { mutableStateOf(false) }
    // Saveable: turning the phone while recording rebuilds this screen when
    // it comes back, and a plain remember would forget which file the
    // camera was writing to - the recording was then silently dropped.
    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingCameraAction by rememberSaveable { mutableStateOf<PendingCameraAction?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val voice = rememberVoiceAssistant()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    LaunchedEffect(state.deleted) {
        if (state.deleted) onDeleted()
    }
    SpeakErrorEffect(voice, state.error)

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this campaign?") },
            text = { Text("It'll disappear from the campaigns list, but stays recoverable - an admin can restore it from Deleted Campaigns.") },
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

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingCameraUri?.let { viewModel.pickMedia(context, it) }
    }
    val recordVideo = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success) pendingCameraUri?.let { viewModel.pickMedia(context, it) }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) return@rememberLauncherForActivityResult
        when (pendingCameraAction) {
            PendingCameraAction.PHOTO -> {
                val uri = createCameraCapturePhotoUri(context)
                pendingCameraUri = uri
                takePhoto.launch(uri)
            }
            PendingCameraAction.VIDEO -> {
                val uri = createCameraCaptureVideoUri(context)
                pendingCameraUri = uri
                recordVideo.launch(uri)
            }
            null -> Unit
        }
    }
    // A single mixed picker - business ask: "allow both video or image" in
    // one slot, so the gallery option doesn't force a choice upfront.
    val pickFromGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.pickMedia(context, uri)
    }

    if (showPickerChoice) {
        AlertDialog(
            onDismissRequest = { showPickerChoice = false },
            title = { Text("Add campaign media") },
            text = {
                Column {
                    Text(
                        "No worries if you skip this - a campaign can go out without media and have one added later. " +
                            "Only one media item per campaign; picking a new one replaces the old, so it's easy to " +
                            "switch between a video and an image if you change your mind.",
                        color = DateroemTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    TextButton(
                        onClick = {
                            showPickerChoice = false
                            pendingCameraAction = PendingCameraAction.PHOTO
                            cameraPermission.launch(android.Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Take Photo") }
                    TextButton(
                        onClick = {
                            showPickerChoice = false
                            pendingCameraAction = PendingCameraAction.VIDEO
                            cameraPermission.launch(android.Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Record Video") }
                    TextButton(
                        onClick = {
                            showPickerChoice = false
                            pickFromGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Choose from Gallery") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPickerChoice = false }) { Text("Cancel") }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(DateroemBackground, DateroemSheetEnd)))
    ) {
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
                if (isEditing) "Edit Campaign" else "New Campaign",
                color = DateroemText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (state.restoredDraft) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DateroemAccent.copy(alpha = 0.14f))
                        .padding(start = 12.dp)
                ) {
                    Text(
                        "Your unsaved changes from before are back.",
                        color = DateroemText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = viewModel::discardDraft) { Text("Discard") }
                }
            }

            val previewSource = state.pickedMediaUri?.toString() ?: state.existingMediaUrl
            val hasMedia = previewSource != null

            if (state.effectiveMediaType == MEDIA_TYPE_IMAGE && hasMedia) {
                ProductThumbnail(
                    imageUrl = previewSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showPickerChoice = true },
                    cornerRadius = 12.dp
                )
                Text(
                    "Image attached - tap to change",
                    color = DateroemTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else if (state.effectiveMediaType == MEDIA_TYPE_VIDEO && hasMedia && previewSource != null) {
                // "i want to ensure the videos are seen on all sections
                // well" - an actual playable preview instead of a static
                // "video attached" badge, so a bad/corrupt upload is
                // obvious immediately instead of only found out later.
                VideoPlayer(
                    url = previewSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    Text(
                        "Video attached",
                        color = DateroemTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = { showPickerChoice = true }) {
                        Text("Change")
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DateroemSurface)
                        .clickable { showPickerChoice = true },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(AppIcons.Megaphone, contentDescription = null, tint = DateroemAccent, modifier = Modifier.size(34.dp))
                        Text(
                            "Tap to add a promo video or image (optional)",
                            color = DateroemTextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }


            VoiceAssistantToggle(voice, modifier = Modifier.padding(top = 12.dp))

            VoiceOutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                assistant = voice,
                voiceKey = "name",
                label = { Text("Campaign name *") },
                singleLine = true,
                modifier = Modifier.padding(top = 16.dp)
            )
            // "can we have a selector for channel not typing but having a
            // radio box for the channel options or an all if one wants to
            // send it to all areas" - a fixed set instead of free text, same
            // FilterChip-as-radio pattern already used for Status below.
            Text("Channel", color = DateroemTextMuted, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                CampaignChannel.entries.forEach { option ->
                    FilterChip(
                        selected = state.channel == option.value,
                        onClick = { viewModel.updateChannel(option.value) },
                        label = { Text(option.value) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DateroemAccent,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField(
                    label = "Start",
                    value = state.startAt,
                    onValueChange = viewModel::updateStartAt,
                    modifier = Modifier.weight(1f)
                )
                DateField(
                    label = "End",
                    value = state.endAt,
                    onValueChange = viewModel::updateEndAt,
                    modifier = Modifier.weight(1f)
                )
            }

            Text("Status", color = DateroemTextMuted, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampaignStatus.entries.forEach { status ->
                    FilterChip(
                        selected = state.status == status,
                        onClick = { viewModel.updateStatus(status) },
                        label = { Text(status.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DateroemAccent,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            CampaignProductsField(
                products = state.products,
                loaded = state.productsLoaded,
                error = state.productsError,
                selectedIds = state.selectedProductIds,
                onChange = viewModel::setSelectedProducts,
                onRetry = viewModel::retryProducts,
                modifier = Modifier.padding(top = 16.dp)
            )
            // The website's Featured page skips a campaign with nothing to
            // buy - say so before an Active one goes out empty.
            if (state.status == CampaignStatus.ACTIVE && state.productsLoaded && state.selectedProductIds.isEmpty()) {
                Text(
                    "Customers only see a campaign on the website once it has at least one product.",
                    color = DateroemAccent,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        viewModel.forgetChanges()
                        onCancel()
                    },
                    modifier = Modifier.weight(1f)
                ) {
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
                        Text(if (isEditing) "Save Changes" else "Create Campaign", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (isEditing) {
                // "the delete button behind the trip selector button at
                // the bottom of the screen ... preventing it from being
                // too close to the rest of the fields for accessibility
                // and risk prevention" - see ProductFormScreen's own copy
                // of this fix for the full reasoning: the trip-selector
                // FAB floats at a fixed bottom-center position regardless
                // of scroll, so more gap above separates this from Save/
                // Cancel and the spacer below clears the FAB's footprint.
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = !state.deleting,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                ) {
                    if (state.deleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.error, strokeWidth = 2.dp)
                    } else {
                        Text("Delete Campaign", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
}

private fun createCameraCapturePhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun createCameraCaptureVideoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.mp4")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
