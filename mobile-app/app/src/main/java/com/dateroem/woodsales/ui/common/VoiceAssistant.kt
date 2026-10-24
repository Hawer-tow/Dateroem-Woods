package com.dateroem.woodsales.ui.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TextButton
import androidx.core.content.ContextCompat
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import java.util.Locale
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size

/**
 * "you click a field it listens fills you click the next the same until
 * the end when one says stop or switches off the voice assistant ... i
 * want to eliminate the need to say the fields name ... if i say only the
 * actual name e.g mwangi household it will still write in it" - a
 * complete redesign from the earlier "say the label then the value"
 * flow: with the assistant switched on, focusing any wired field starts
 * listening for THAT field alone and whatever is heard becomes its raw
 * value directly - no label needed at all, since which field to fill is
 * now unambiguous (it's whichever one you tapped).
 *
 * One instance per form (via rememberVoiceAssistant()), reusing a single
 * SpeechRecognizer across every field on that screen rather than creating
 * a new one per field.
 */
class VoiceAssistant(private val context: Context) {
    var enabled by mutableStateOf(false)
        private set
    var activeFieldKey by mutableStateOf<String?>(null)
        private set
    var partialText by mutableStateOf("")
        private set

    // "my voice command does not work anymore" - the phone had the app's
    // microphone switched off, the app never asked for it, and the speech
    // service's refusal was dropped silently, so tapping a mic did nothing.
    // Now the app asks, and any problem is said out loud on the screen:
    // under the switch, or under the field whose mic was tapped.
    var notice by mutableStateOf<VoiceNotice?>(null)
        private set

    // Opens Android's "allow the microphone?" question; wired up by
    // rememberVoiceAssistant().
    internal var askForMicrophone: () -> Unit = {}
    private var afterMicrophone: (() -> Unit)? = null
    private var askedForField: String? = null

    private val recognizer = ContinuousVoiceRecognizer(context)
    private var tts: TextToSpeech? = TextToSpeech(context) { }

    private fun hasMicrophone(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun withMicrophone(fieldKey: String?, action: () -> Unit) {
        if (hasMicrophone()) {
            action()
            return
        }
        afterMicrophone = action
        askedForField = fieldKey
        askForMicrophone()
    }

    internal fun onMicrophoneAnswer(granted: Boolean) {
        val action = afterMicrophone
        afterMicrophone = null
        if (granted && hasMicrophone()) {
            notice = null
            action?.invoke()
        } else {
            notice = VoiceNotice(
                "Voice needs the microphone, and it's switched off for this app. " +
                    "Allow it under Permissions > Microphone.",
                fieldKey = askedForField,
                needsSettings = true
            )
        }
    }

    fun setVoiceEnabled(value: Boolean) {
        if (!value) {
            enabled = false
            notice = null
            cancel()
            return
        }
        withMicrophone(fieldKey = null) {
            enabled = true
            notice = null
            // Warm up the recognizer right when the switch flips on, not
            // on the first field tap - gives the system speech service's
            // async binding a head start before it's actually needed.
            recognizer.prepare()
        }
    }

    // "click a field it listens fills" - re-entering the same field
    // that's already listening (recomposition can re-fire focus
    // callbacks) is a no-op rather than restarting the recognizer.
    fun listenFor(fieldKey: String, numeric: Boolean, onValue: (String) -> Unit) {
        if (!enabled || activeFieldKey == fieldKey) return
        // The permission can lapse while the switch stays on (Android takes
        // back an "only this time" answer) - ask again, then listen.
        if (!hasMicrophone()) {
            withMicrophone(fieldKey) { listenFor(fieldKey, numeric, onValue) }
            return
        }
        notice = null
        recognizer.stop()
        activeFieldKey = fieldKey
        partialText = ""

        recognizer.onPartial = { partialText = it }
        recognizer.onListeningChanged = { listening ->
            if (!listening && activeFieldKey == fieldKey) {
                activeFieldKey = null
                partialText = ""
            }
        }
        recognizer.onError = { message ->
            if (activeFieldKey == fieldKey) {
                activeFieldKey = null
                partialText = ""
            }
            notice = VoiceNotice(message, fieldKey = fieldKey, needsSettings = message.contains("permission"))
        }
        recognizer.onFinalSegment = { heard ->
            val said = if (containsStopWord(heard)) truncateAtStopWord(heard) else heard
            val trimmed = said.trim()
            if (trimmed.isNotEmpty()) {
                val value = if (numeric) {
                    wordsToNumber(trimmed)?.let { n ->
                        if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()
                    } ?: trimmed
                } else {
                    trimmed
                }
                onValue(value)
            }
            // One value per field tap, then stop - "you click a field it
            // listens fills you click the next" implies each tap is its
            // own turn, not an open mic waiting for more about this field.
            recognizer.stop()
        }
        recognizer.start()
    }

    fun cancel() {
        recognizer.stop()
        activeFieldKey = null
        partialText = ""
    }

    // "it can even speak and tell you you need to fill the number of
    // trees before you can create this object" - reuses the exact
    // message already shown as the on-screen error text, spoken aloud
    // only while voice mode is on (never surprises someone who hasn't
    // opted into voice at all).
    fun speak(message: String) {
        if (!enabled) return
        tts?.language = Locale.ENGLISH
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun destroy() {
        recognizer.destroy()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

// A voice problem worth telling the person about; fieldKey says which
// field's mic it came from (null = the main switch).
data class VoiceNotice(val message: String, val fieldKey: String? = null, val needsSettings: Boolean = false)

@Composable
fun rememberVoiceAssistant(): VoiceAssistant {
    val context = LocalContext.current
    val assistant = remember { VoiceAssistant(context) }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        assistant.onMicrophoneAnswer(granted)
    }
    SideEffect {
        assistant.askForMicrophone = { microphone.launch(Manifest.permission.RECORD_AUDIO) }
    }
    DisposableEffect(Unit) {
        onDispose { assistant.destroy() }
    }
    return assistant
}

@Composable
private fun VoiceNoticeLine(notice: VoiceNotice, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        ErrorBox(notice.message, small = true)
        if (notice.needsSettings) {
            // Once Android has been told "don't allow", it stops asking -
            // the app's own settings page is the only way back.
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            }) { Text("Open settings") }
        }
    }
}

// Speaks a validation error aloud (once per new error) when the
// assistant is switched on - drop this once near the top of any form
// that already shows state.error as text.
@Composable
fun SpeakErrorEffect(assistant: VoiceAssistant, error: String?) {
    LaunchedEffect(error) {
        if (error != null) assistant.speak(error)
    }
}

// "still not there i delete everything i click it say the correct one
// still fails" - traced to fighting the text field's own internal tap/
// focus/cursor gesture handling for the same touch target (the earlier
// onFocusChanged + onTap approach): a re-tap on an already-focused field
// is exactly the gesture the field itself uses for repositioning the
// cursor, and that internal handling was winning the race in ways that
// couldn't be reliably out-guessed without live-testing every device.
//
// This replaces that whole approach with a dedicated mic icon inside the
// field - a separate, ordinary IconButton with its own independent click
// handler, so there is no shared touch target and nothing to race
// against. It also incidentally fixes the original keyboard-popping
// complaint for free: tapping the mic never focuses the field at all
// (only tapping the actual text does that, which is completely
// untouched now), so no keyboard-suppression trick is needed anymore -
// tap the text to type normally, tap the mic to talk, either one always
// works, every time, including retries after editing by hand.
@Composable
fun VoiceOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    assistant: VoiceAssistant,
    voiceKey: String,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    numeric: Boolean = false,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DateroemAccent,
                unfocusedBorderColor = DateroemTextMuted.copy(alpha = 0.4f)
            ),
            trailingIcon = if (!assistant.enabled) null else {
                {
                    IconButton(onClick = { assistant.listenFor(voiceKey, numeric, onValueChange) }) {
                        Icon(
                            AppIcons.Mic,
                            contentDescription = "Fill by voice",
                            tint = if (assistant.activeFieldKey == voiceKey) DateroemAccent else DateroemTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (assistant.activeFieldKey == voiceKey) {
            IconLabel(
                AppIcons.Mic,
                if (assistant.partialText.isNotBlank()) "“${assistant.partialText}”" else "Listening...",
                color = DateroemAccent,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        assistant.notice?.takeIf { it.fieldKey == voiceKey }?.let {
            VoiceNoticeLine(it, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
fun VoiceAssistantToggle(assistant: VoiceAssistant, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = assistant.enabled,
                onCheckedChange = { assistant.setVoiceEnabled(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = DateroemAccent)
            )
            IconLabel(
                AppIcons.Mic,
                "Voice Assistant" + if (assistant.enabled) " - tap the mic in a field to fill it by voice" else "",
                color = if (assistant.enabled) DateroemText else DateroemTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        assistant.notice?.takeIf { it.fieldKey == null }?.let {
            VoiceNoticeLine(it, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
