package com.dateroem.woodsales.ui.marketing

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.dateroem.woodsales.data.model.ShowcasePicture
import com.dateroem.woodsales.ui.common.AppIcons
import com.dateroem.woodsales.ui.common.resolveMediaUrl
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodButton
import com.dateroem.woodsales.ui.theme.WoodFab
import java.io.File
import com.dateroem.woodsales.ui.common.ErrorBox

// "pictures that can be used to show case what bulk orders look like so that
// customers can see a template of what is meant by bulk orders" - the photos
// behind the website's Bulk Orders slideshow, kept here by Marketers, Admins
// and the SuperAdmin (reached from Marketing's top bar). Not products: just
// example photos with an optional caption, in the order they play.
@Composable
fun ShowcaseScreen(viewModel: ShowcaseViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var choosingSource by remember { mutableStateOf(false) }
    // Saveable: turning the phone while the camera is open rebuilds this screen.
    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    // The photo just taken or chosen, waiting for its caption.
    var newPicture by rememberSaveable { mutableStateOf<Uri?>(null) }
    var editing by remember { mutableStateOf<ShowcasePicture?>(null) }
    var removing by remember { mutableStateOf<ShowcasePicture?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) newPicture = pendingCameraUri
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = createShowcaseCaptureUri(context)
            pendingCameraUri = uri
            takePicture.launch(uri)
        }
    }
    val pickFromGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) newPicture = uri
    }

    val full = state.pictures.size >= SHOWCASE_MAX_PICTURES

    Scaffold(
        containerColor = DateroemBackground,
        floatingActionButton = {
            if (!state.loading && state.loadError == null && !full) {
                WoodFab(onClick = { if (!state.busy) choosingSource = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add a picture")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
                state.loadError != null -> Column(modifier = Modifier.align(Alignment.Center).padding(24.dp)) {
                    Text("Couldn't load the pictures", color = MaterialTheme.colorScheme.error)
                    ErrorBox(state.loadError ?: "", modifier = Modifier.padding(top = 8.dp))
                    WoodButton(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) { Text("Retry") }
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    item {
                        Text(
                            "These play as a slideshow on the website's Bulk Orders page, so customers can see what a " +
                                "bulk order looks like. Examples only - not products. The first one shows first.",
                            color = DateroemTextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Text(
                            when {
                                state.pictures.isEmpty() -> "No pictures yet - tap + to add the first. Until then the page shows no slideshow."
                                full -> "${state.pictures.size} of $SHOWCASE_MAX_PICTURES pictures - remove one to add another."
                                else -> "${state.pictures.size} of $SHOWCASE_MAX_PICTURES pictures"
                            },
                            color = DateroemAccent,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        if (state.busy) {
                            LinearProgressIndicator(color = DateroemAccent, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                        }
                        state.error?.let {
                            ErrorBox(it, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                    itemsIndexed(state.pictures, key = { _, p -> p.showcaseId }) { index, picture ->
                        PictureCard(
                            position = index + 1,
                            picture = picture,
                            enabled = !state.busy,
                            canMoveUp = index > 0,
                            canMoveDown = index < state.pictures.lastIndex,
                            onMoveUp = { viewModel.move(index, -1) },
                            onMoveDown = { viewModel.move(index, 1) },
                            onCaption = { editing = picture },
                            onRemove = { removing = picture }
                        )
                    }
                    // Room for the + button and the bottom dock.
                    item { Spacer(Modifier.height(140.dp)) }
                }
            }
        }
    }

    if (choosingSource) {
        AlertDialog(
            onDismissRequest = { choosingSource = false },
            title = { Text("Add a bulk order picture") },
            text = { Text("A photo of a real bulk order - loaded lorries, stacked poles, timber ready at a site.") },
            confirmButton = {
                TextButton(onClick = {
                    choosingSource = false
                    cameraPermission.launch(android.Manifest.permission.CAMERA)
                }) { Text("Take Photo") }
            },
            dismissButton = {
                TextButton(onClick = {
                    choosingSource = false
                    pickFromGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Choose from Gallery") }
            }
        )
    }

    newPicture?.let { uri ->
        CaptionDialog(
            title = "New picture",
            imageUrl = uri.toString(),
            initial = "",
            confirmLabel = "Add",
            onConfirm = { caption ->
                newPicture = null
                viewModel.add(context, uri, caption)
            },
            onDismiss = { newPicture = null }
        )
    }

    editing?.let { picture ->
        CaptionDialog(
            title = "Caption",
            imageUrl = picture.image,
            initial = picture.caption ?: "",
            confirmLabel = "Save",
            onConfirm = { caption ->
                editing = null
                viewModel.editCaption(picture.showcaseId, caption)
            },
            onDismiss = { editing = null }
        )
    }

    removing?.let { picture ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove this picture?") },
            text = { Text("It leaves the Bulk Orders slideshow straight away.") },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    viewModel.remove(picture.showcaseId)
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text("Cancel", color = DateroemTextMuted) }
            }
        )
    }
}

@Composable
private fun PictureCard(
    position: Int,
    picture: ShowcasePicture,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onCaption: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        ShowcaseImage(picture.image, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$position",
                color = DateroemAccent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 10.dp)
            )
            Text(
                picture.caption ?: "No caption",
                color = if (picture.caption == null) DateroemTextMuted else DateroemText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onMoveUp, enabled = enabled && canMoveUp) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Show earlier")
            }
            IconButton(onClick = onMoveDown, enabled = enabled && canMoveDown) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Show later")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onCaption, enabled = enabled) {
                Text(if (picture.caption == null) "Add caption" else "Edit caption", color = DateroemAccent)
            }
            TextButton(onClick = onRemove, enabled = enabled) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// The whole picture on a dark 16:9 frame - the way the website shows it, so
// an upright phone photo isn't cut off here either.
@Composable
private fun ShowcaseImage(imageUrl: String?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(DateroemBackground), contentAlignment = Alignment.Center) {
        if (imageUrl.isNullOrBlank()) {
            Icon(AppIcons.Image, contentDescription = null, tint = DateroemTextMuted, modifier = Modifier.size(32.dp))
        } else {
            SubcomposeAsyncImage(
                model = resolveMediaUrl(imageUrl),
                contentDescription = "Bulk order picture",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                loading = { CircularProgressIndicator(modifier = Modifier.size(22.dp), color = DateroemAccent, strokeWidth = 2.dp) },
                error = { Icon(AppIcons.Image, contentDescription = null, tint = DateroemTextMuted, modifier = Modifier.size(32.dp)) },
                success = { SubcomposeAsyncImageContent() }
            )
        }
    }
}

@Composable
private fun CaptionDialog(
    title: String,
    imageUrl: String?,
    initial: String,
    confirmLabel: String,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var caption by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                ShowcaseImage(
                    imageUrl,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))
                )
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it.take(SHOWCASE_MAX_CAPTION) },
                    label = { Text("Caption (optional)") },
                    placeholder = { Text("e.g. 300 eucalyptus poles for a site in Thika") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                Text(
                    "${caption.length} / $SHOWCASE_MAX_CAPTION - shown over the picture on the website",
                    color = DateroemTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(caption.ifBlank { null }) }) { Text(confirmLabel, color = DateroemAccent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = DateroemTextMuted) }
        }
    )
}

private fun createShowcaseCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "showcase_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
