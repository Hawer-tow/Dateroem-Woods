package com.dateroem.woodsales.ui.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

// "in the tree creation one can use voice control to add data" -
// wordsToNumber is the Kotlin port of the desktop app's own
// voice_input.py helper of the same job (English number-word -> value).
// containsStopWord/truncateAtStopWord and ContinuousVoiceRecognizer below
// back VoiceAssistant.kt's per-field "click a field, it listens, fills"
// flow, which replaced this file's earlier whole-form "say the label
// then the value" design.
private val SMALL_NUMBERS = mapOf(
    "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
    "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
    "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
    "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19
)
private val TENS = mapOf(
    "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
    "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
)

// Best-effort English number-word -> value conversion (0-99 plus simple
// decimals like "six point five"), used when the speech engine renders a
// spoken number as words instead of digits. Falls back to any literal
// digits already present; returns null rather than guessing when neither
// works, so the field is left for the user to fill/correct by hand.
fun wordsToNumber(text: String): Double? {
    val lowered = text.trim().lowercase()

    Regex("""-?\d+(\.\d+)?""").find(lowered)?.let { return it.value.toDouble() }

    val words = Regex("[a-z]+").findAll(lowered).map { it.value }.toList()
    if (words.isEmpty()) return null

    val pointIdx = words.indexOf("point")
    if (pointIdx != -1) {
        val whole = wordsToNumber(words.subList(0, pointIdx).joinToString(" "))
        val fracWords = words.subList(pointIdx + 1, words.size)
        if (whole == null || fracWords.isEmpty()) return null
        val fracDigits = fracWords.mapNotNull { SMALL_NUMBERS[it] }.joinToString("")
        if (fracDigits.isEmpty()) return null
        return "${whole.toInt()}.$fracDigits".toDoubleOrNull()
    }

    var total = 0
    var matched = false
    for (word in words) {
        TENS[word]?.let { total += it; matched = true }
        SMALL_NUMBERS[word]?.let { total += it; matched = true }
    }
    return if (matched) total.toDouble() else null
}

private val STOP_WORD = Regex("""\bstop\b""", RegexOption.IGNORE_CASE)

fun containsStopWord(text: String): Boolean = STOP_WORD.containsMatchIn(text)

// "it only stops listening if the word stop is used" - if they say "tree
// type is oak, stop" in one breath, only "tree type is oak" should be
// parsed for field values; everything from "stop" onward is just the
// stop command itself, not part of any field's dictated value.
fun truncateAtStopWord(text: String): String {
    val match = STOP_WORD.find(text) ?: return text
    return text.substring(0, match.range.first)
}

/**
 * "make it continuous such that i can see the speaking area but i can
 * also see the field ... it only stops listening if the word stop is
 * used so that it can fill one field to the other to the next" - Android's
 * SpeechRecognizer only ever recognizes one utterance per startListening()
 * call (there's no native "keep listening indefinitely" mode like
 * desktop's SAPI dictation), so continuous listening here means
 * automatically restarting it after each utterance - onFinalSegment fires
 * once per pause in speech, letting the caller fill whatever fields that
 * segment mentioned immediately, then listening resumes for the next one
 * until "stop" is heard or stop() is called.
 */
class ContinuousVoiceRecognizer(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    var onPartial: (String) -> Unit = {}
    var onFinalSegment: (String) -> Unit = {}
    var onListeningChanged: (Boolean) -> Unit = {}
    var onError: (String) -> Unit = {}

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    // "the first field immediately after toggling on the voice assistant
    // doesn't capture data immediately" - SpeechRecognizer.
    // createSpeechRecognizer() binds to the system's speech service
    // asynchronously, and calling startListening() before that binding
    // finishes is exactly what was silently swallowing the very first
    // listen right after switching the assistant on (later fields worked
    // because by then the service had finished binding). Creating the
    // recognizer the moment the assistant is switched on - well before
    // the user's first tap - gives that binding a head start.
    fun prepare() {
        if (recognizer == null && isAvailable()) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
                it.setRecognitionListener(listener)
            }
        }
    }

    fun start() {
        if (!isAvailable()) {
            onError("Voice input isn't available on this device.")
            return
        }
        prepare()
        listening = true
        listenOnce()
    }

    fun stop() {
        if (!listening) return
        listening = false
        recognizer?.stopListening()
        onListeningChanged(false)
    }

    fun destroy() {
        listening = false
        recognizer?.destroy()
        recognizer = null
    }

    private fun listenOnce() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        onListeningChanged(true)
        recognizer?.startListening(intent)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle) {
            partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.let(onPartial)
        }

        override fun onResults(results: Bundle) {
            val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank()) onFinalSegment(text)
            // Still in continuous mode (stop() wasn't called from within
            // onFinalSegment) - immediately listen for the next segment,
            // covering the "one field to the next" flow without the user
            // needing to re-tap anything between fields.
            if (listening) listenOnce()
        }

        override fun onError(error: Int) {
            // A silence/no-speech timeout is routine while continuously
            // listening (the pause between fields) - not a real failure,
            // just restart and keep waiting rather than giving up.
            // ERROR_CLIENT is the other routine one here specifically:
            // it's what fires if startListening() lands before the
            // speech service has finished binding (the "first field
            // right after switching voice on" race) - worth one silent
            // retry rather than surfacing as a failure.
            val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                error == SpeechRecognizer.ERROR_CLIENT
            if (listening && recoverable) {
                listenOnce()
            } else {
                val wasListening = listening
                listening = false
                onListeningChanged(false)
                if (wasListening && !recoverable) {
                    onError("Voice input stopped: ${errorMessage(error)}")
                }
            }
        }
    }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "audio error"
        SpeechRecognizer.ERROR_CLIENT -> "client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "microphone permission is needed"
        SpeechRecognizer.ERROR_NETWORK -> "network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer is busy"
        SpeechRecognizer.ERROR_SERVER -> "server error"
        else -> "unknown error ($error)"
    }
}
