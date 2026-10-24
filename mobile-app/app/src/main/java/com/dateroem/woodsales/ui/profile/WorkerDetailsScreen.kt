package com.dateroem.woodsales.ui.profile

import android.Manifest
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.dateroem.woodsales.data.network.JsonMap
import com.dateroem.woodsales.ui.common.DateField
import com.dateroem.woodsales.ui.orders.flag
import com.dateroem.woodsales.ui.orders.maps
import com.dateroem.woodsales.ui.orders.text
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodButton
import com.dateroem.woodsales.ui.theme.WoodOn
import java.io.File
import com.dateroem.woodsales.ui.common.ErrorBox

private val VerifiedGreen = Color(0xFF43A047)
private val ChangesRed = Color(0xFFE53935)

/**
 * Worker details (migration 040), from My profile: the staff member's ID or
 * passport number, KRA PIN, NSSF and SHA numbers, next of kin and driving
 * licence, and photos of their documents. Only the SuperAdmin can open them;
 * numbers and photos are stored encrypted.
 */
@Composable
fun WorkerDetailsScreen(viewModel: WorkerDetailsViewModel) {
    val state by viewModel.state.collectAsState()
    val record = state.record

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
            return@Box
        }
        if (record == null) {
            Column(modifier = Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ErrorBox(state.error ?: "Couldn't load your details")
                TextButton(onClick = viewModel::load) { Text("Try again") }
            }
            return@Box
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StatusCard(record)
            DetailsCard(record, state, viewModel)
            DocumentsCard(record, state, viewModel)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DateroemSurface, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Text(title, color = DateroemText, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun StatusCard(record: JsonMap) {
    val status = record.text("status") ?: "NOT_STARTED"
    val (title, colour) = when (status) {
        "VERIFIED" -> "Verified" to VerifiedGreen
        "SUBMITTED" -> "Waiting for the SuperAdmin to check" to DateroemAccent
        "NEEDS_CHANGES" -> "Needs changes" to ChangesRed
        else -> "Not sent yet" to DateroemTextMuted
    }
    Section("Where it stands") {
        Text(title, color = colour, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        record.text("statusNote")?.takeIf { status == "NEEDS_CHANGES" }?.let {
            Text(it, color = DateroemText, modifier = Modifier.padding(top = 4.dp))
        }
        Text(
            when (status) {
                "NOT_STARTED" -> "Fill in the details and add the photos marked *. They go to the SuperAdmin to check once everything needed is there."
                "SUBMITTED" -> "Changing anything sends it to be checked again."
                "VERIFIED" -> "Changing anything sends it to be checked again."
                else -> "Correct what the note says - it goes back to be checked."
            },
            color = DateroemTextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            "Only the SuperAdmin can open these. Numbers and photos are stored encrypted.",
            color = DateroemTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun DetailsCard(record: JsonMap, state: WorkerDetailsState, viewModel: WorkerDetailsViewModel) {
    val fields = record.maps("fields")
    val values = remember(record) {
        mutableStateMapOf<String, String>().apply {
            fields.forEach { f -> put(f.text("fieldKey") ?: "", f.text("value") ?: "") }
        }
    }
    var shown by remember { mutableStateOf(false) }
    Section("Your details") {
        TextButton(onClick = { shown = !shown }) { Text(if (shown) "Hide numbers" else "Show numbers", fontSize = 13.sp) }
        fields.forEach { field ->
            val key = field.text("fieldKey") ?: return@forEach
            val label = (field.text("label") ?: key) + if (field.flag("isRequired")) " *" else ""
            val hint = field.text("hint")
            when (field.text("kind")) {
                "date" -> DateField(
                    label = label,
                    value = values[key] ?: "",
                    onValueChange = { values[key] = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
                else -> OutlinedTextField(
                    value = values[key] ?: "",
                    onValueChange = { values[key] = it },
                    label = { Text(label) },
                    supportingText = hint?.let { { Text(it, fontSize = 11.sp) } },
                    singleLine = true,
                    visualTransformation = if (field.flag("isSensitive") && !shown) PasswordVisualTransformation('•') else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (field.text("kind")) {
                            "phone" -> KeyboardType.Phone
                            "number" -> KeyboardType.Number
                            else -> KeyboardType.Text
                        }
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }
        }
        state.saveError?.let { ErrorBox(it, modifier = Modifier.padding(top = 6.dp), fontSize = 13.sp) }
        state.saveNote?.let { Text(it, color = DateroemAccent, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        WoodButton(
            onClick = { viewModel.save(values.mapValues { (_, v) -> v.trim().ifBlank { null } }) },
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            if (state.saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = WoodOn)
            else Text("Save details", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DocumentsCard(record: JsonMap, state: WorkerDetailsState, viewModel: WorkerDetailsViewModel) {
    val context = LocalContext.current
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val type = target
        val uri = cameraUri
        if (success && type != null && uri != null) viewModel.upload(type, uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = captureUri(context)
            cameraUri = uri
            takePicture.launch(uri)
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val type = target
        if (uri != null && type != null) viewModel.upload(type, uri)
    }

    Section("Document photos") {
        Text("Photograph each one flat, in good light, with every corner showing.", color = DateroemTextMuted, fontSize = 13.sp)
        state.uploadError?.let { ErrorBox(it, modifier = Modifier.padding(top = 6.dp), fontSize = 13.sp) }
        record.maps("docTypes").forEach { doc ->
            val key = doc.text("typeKey") ?: return@forEach
            val sent = doc.text("uploadedAt")
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text((doc.text("label") ?: key) + if (doc.flag("isRequired")) " *" else "", color = DateroemText, fontWeight = FontWeight.SemiBold)
                doc.text("hint")?.let { Text(it, color = DateroemTextMuted, fontSize = 12.sp) }
                Text(
                    if (sent != null) "Sent ${sent.take(10)}" else "Not sent yet",
                    color = if (sent != null) VerifiedGreen else DateroemTextMuted, fontSize = 12.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.uploading == key) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = DateroemAccent)
                        Text("  Sending...", color = DateroemTextMuted, fontSize = 13.sp)
                    } else {
                        TextButton(onClick = {
                            target = key
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }, enabled = state.uploading == null) { Text(if (sent != null) "Take again" else "Take photo") }
                        TextButton(onClick = {
                            target = key
                            pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }, enabled = state.uploading == null) { Text("Choose from gallery") }
                    }
                }
            }
        }
    }
}

private fun captureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "worker_doc_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
