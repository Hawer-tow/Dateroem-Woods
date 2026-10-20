"""Voice-to-text for form filling, using Windows' own built-in speech
engine (SAPI, via pywin32) rather than a downloaded model - "is that ok to
proceed... does that work without the anthropic subscription" - yes: this
needs no API key, no subscription, no internet, and no large download
(pywin32 is ~7MB with prebuilt wheels, vs. hundreds of MB for a local
Whisper install, which this machine's network made painful to even
install). SAPI's dictation accuracy is lower than Whisper's for open-
ended speech, but this only ever needs to catch field labels and short
values ("household is Kamau's farm, number of trees is eight") - a
constrained vocabulary, not free dictation - so it's a reasonable fit.
English only for now, per "in the voice control it can be done in
english first then we can proceed to the other languages later".
"""
import re
import threading
import time

# pythoncom and win32com are loaded in listen(), the first time someone
# uses voice: loading them added about a second to every start of the app.

_SMALL_NUMBERS = {
    "zero": 0, "one": 1, "two": 2, "three": 3, "four": 4, "five": 5, "six": 6,
    "seven": 7, "eight": 8, "nine": 9, "ten": 10, "eleven": 11, "twelve": 12,
    "thirteen": 13, "fourteen": 14, "fifteen": 15, "sixteen": 16, "seventeen": 17,
    "eighteen": 18, "nineteen": 19,
}
_TENS = {
    "twenty": 20, "thirty": 30, "forty": 40, "fifty": 50, "sixty": 60,
    "seventy": 70, "eighty": 80, "ninety": 90,
}


def words_to_number(text: str) -> float | None:
    """Best-effort English number-word -> value conversion (covers 0-99
    plus simple decimals like "six point five"), used when SAPI renders a
    spoken number as words instead of digits. Falls back to any literal
    digits already in the text; returns None rather than guessing when
    neither works, so a field is left blank for the admin to fill by hand
    rather than silently wrong - same "never silently guess" rule as the
    image-scan feature never auto-saving.
    """
    text = text.strip().lower()

    digit_match = re.search(r"-?\d+(\.\d+)?", text)
    if digit_match:
        return float(digit_match.group())

    words = re.findall(r"[a-z]+", text)
    if not words:
        return None

    if "point" in words:
        idx = words.index("point")
        whole = words_to_number(" ".join(words[:idx]))
        frac_words = words[idx + 1:]
        if whole is None or not frac_words:
            return None
        frac_digits = "".join(str(_SMALL_NUMBERS.get(w, "")) for w in frac_words)
        if not frac_digits:
            return None
        return float(f"{int(whole)}.{frac_digits}")

    total = 0
    matched = False
    i = 0
    while i < len(words):
        word = words[i]
        if word in _TENS:
            total += _TENS[word]
            matched = True
        elif word in _SMALL_NUMBERS:
            total += _SMALL_NUMBERS[word]
            matched = True
        i += 1
    return float(total) if matched else None


def parse_voice_fields(transcript: str, fields: list) -> dict:
    """Finds each field's label mentioned in the transcript and takes the
    words between it and the next recognized label as that field's raw
    value - "household is Kamau's farm, number of trees is eight" fills
    household="Kamau's farm", noTrees=8. Fields never matched are simply
    left out of the result (the admin fills those by hand or re-records),
    same review-before-save principle as the image scan.
    """
    lowered = transcript.lower()
    matches = []
    for field in fields:
        if field.type in ("checkbox", "image", "media"):
            continue
        label = field.label.lower()
        idx = lowered.find(label)
        if idx != -1:
            matches.append((idx, idx + len(label), field))

    if not matches:
        return {}

    matches.sort(key=lambda m: m[0])
    values: dict = {}
    for i, (_start, end, field) in enumerate(matches):
        next_start = matches[i + 1][0] if i + 1 < len(matches) else len(transcript)
        raw = transcript[end:next_start].strip(" ,.:-")
        # Strip a leading filler word ("is"/"equals"/"of") some phrasings
        # naturally include, e.g. "household is Kamau's farm".
        raw = re.sub(r"^(is|equals|of)\s+", "", raw, flags=re.IGNORECASE).strip()
        if not raw:
            continue

        if field.type in ("int", "float"):
            number = words_to_number(raw)
            if number is not None:
                values[field.key] = int(number) if field.type == "int" else number
        else:
            values[field.key] = raw
    return values


class VoiceRecognizer:
    """Wraps SAPI's shared recognizer in dictation mode. listen() blocks
    (run it via the existing Worker/QThreadPool background-thread pattern,
    same as every network call in this app) until stop() is called from
    the UI thread or max_seconds elapses, and returns everything heard as
    one string. Uses whatever the system's default microphone and
    recognition language are - no separate audio-capture code needed,
    SAPI owns that.
    """

    def __init__(self):
        self._stop_event = threading.Event()

    def stop(self):
        self._stop_event.set()

    def listen(self, max_seconds: float = 20.0) -> str:
        import pythoncom
        import win32com.client

        pythoncom.CoInitialize()
        try:
            self._stop_event.clear()
            transcript_parts: list[str] = []

            context = win32com.client.Dispatch("SAPI.SpSharedRecoContext")
            grammar = context.CreateGrammar()
            grammar.DictationSetState(1)

            class _Events:
                def OnRecognition(self, _stream_number, _stream_position, _recognition_type, result):
                    phrase = win32com.client.Dispatch(result)
                    text = phrase.PhraseInfo.GetText()
                    if text:
                        transcript_parts.append(text)

            sink = win32com.client.WithEvents(context, _Events)  # noqa: F841 - keeps the event sink alive

            start = time.time()
            while not self._stop_event.is_set() and (time.time() - start) < max_seconds:
                pythoncom.PumpWaitingMessages()
                time.sleep(0.05)

            grammar.DictationSetState(0)
            return " ".join(transcript_parts).strip()
        finally:
            pythoncom.CoUninitialize()
