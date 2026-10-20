"""One generic create/edit form - every section (Products, Marketing,
Delivery Areas, Users, ...) describes its fields as a small spec list and
gets a working dialog, instead of a bespoke QDialog subclass per section.
Mirrors DataTable's "describe the shape, not the widget" approach."""
from typing import Callable, Optional

from PySide6.QtCore import QThreadPool, Qt
from PySide6.QtGui import QGuiApplication
from PySide6.QtWidgets import (
    QCheckBox,
    QComboBox,
    QDialog,
    QDialogButtonBox,
    QDoubleSpinBox,
    QFormLayout,
    QFrame,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QProgressBar,
    QPushButton,
    QScrollArea,
    QSpinBox,
    QTextEdit,
    QVBoxLayout,
    QWidget,
)

from app.core.voice_input import VoiceRecognizer, parse_voice_fields
from app.core.worker import Worker
from app.ui.widgets.busy import set_busy
from app.ui.widgets.date_picker import DatePickerField
from app.ui.widgets.image_picker import ImagePickerField
from app.ui.widgets.media_picker import MediaPickerField
from app.ui.icons import icon



class FieldSpec:
    """type: 'text' | 'textarea' | 'int' | 'float' | 'checkbox' | 'combo' | 'date' | 'image' | 'media'
    ('date' is a calendar picker opening on today - values go in/out as
    YYYY-MM-DD strings; 'image' is a photo picker - a JPEG data URL;
    'media' is a photo - or with allow_video, a photo or video - chosen on
    this computer and uploaded on Save: upload(pending, values) sends it and
    returns {"key", "mediaType"?}, and the key is saved in this field and the
    media type in type_key, if given).
    options (combo only): list of (label, value) tuples."""

    def __init__(
        self,
        key: str,
        label: str,
        type: str = "text",
        required: bool = False,
        options: Optional[list] = None,
        placeholder: str = "",
        minimum: float = 0,
        maximum: float = 1_000_000_000,
        decimals: int = 2,
        height: int = 80,
        upload: Optional[Callable] = None,
        type_key: Optional[str] = None,
        allow_video: bool = False,
    ):
        self.key = key
        self.label = label
        self.type = type
        self.required = required
        self.options = options or []
        self.placeholder = placeholder
        self.minimum = minimum
        self.maximum = maximum
        self.decimals = decimals
        self.height = height  # textarea only
        self.upload = upload  # media only
        self.type_key = type_key  # media only
        self.allow_video = allow_video  # media only


class RecordFormDialog(QDialog):
    """save_fn(values: dict) -> result, run off the UI thread. On success
    the dialog closes; on failure the error shows inline and the dialog
    stays open so nothing typed is lost."""

    def __init__(
        self,
        title: str,
        fields: list[FieldSpec],
        save_fn: Callable[[dict], object],
        initial_values: Optional[dict] = None,
        parent=None,
    ):
        super().__init__(parent)
        self.fields = fields
        self.save_fn = save_fn
        self.initial_values = initial_values or {}
        self._inputs: dict[str, QWidget] = {}
        # See GenericListSection - keeps in-flight Workers from being
        # garbage-collected before their signals fire.
        self._active_workers = []
        self._voice_recognizer: Optional[VoiceRecognizer] = None
        self._voice_listening = False

        self.setWindowTitle(title)
        self.setMinimumWidth(420)

        layout = QVBoxLayout(self)

        # "in the tree creation one can use voice control to add data" -
        # available on every form built from this dialog, not just tree
        # creation, since they all share this one component. English
        # only for now ("in the voice control it can be done in english
        # first then we can proceed to the other languages later").
        voice_row = QHBoxLayout()
        self.voice_btn = QPushButton("Voice Fill")
        self.voice_btn.setIcon(icon("mic"))
        self.voice_btn.clicked.connect(self._toggle_voice)
        voice_row.addWidget(self.voice_btn)
        self.voice_status_label = QLabel("")
        self.voice_status_label.setProperty("role", "muted")
        self.voice_status_label.setWordWrap(True)
        voice_row.addWidget(self.voice_status_label, 1)
        layout.addLayout(voice_row)

        # The fields scroll only when the screen is too short for them all
        # (a form with a photo preview on a small laptop screen); otherwise
        # the dialog simply grows to fit, as before.
        form_holder = QWidget()
        form = QFormLayout(form_holder)
        form.setContentsMargins(0, 0, 0, 0)
        form.setFieldGrowthPolicy(QFormLayout.FieldGrowthPolicy.ExpandingFieldsGrow)
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        scroll.setWidget(form_holder)
        layout.addWidget(scroll, 1)

        for field in fields:
            widget = self._build_input(field)
            self._inputs[field.key] = widget
            label_text = field.label + (" *" if field.required else "")
            form.addRow(label_text, widget)

        screen = QGuiApplication.primaryScreen()
        room = screen.availableGeometry().height() - 220 if screen else 600
        scroll.setMinimumHeight(min(form_holder.sizeHint().height(), max(room, 240)))
        scroll.setMinimumWidth(form_holder.sizeHint().width() + scroll.verticalScrollBar().sizeHint().width())

        self.error_label = QLabel("")
        self.error_label.setProperty("role", "error")
        self.error_label.setWordWrap(True)
        layout.addWidget(self.error_label)

        # Indeterminate bar along the bottom edge of the form while a save
        # is in flight - visible even if the eye isn't on the button.
        self.saving_bar = QProgressBar()
        self.saving_bar.setRange(0, 0)
        self.saving_bar.setTextVisible(False)
        self.saving_bar.setFixedHeight(6)
        self.saving_bar.hide()
        layout.addWidget(self.saving_bar)

        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Save | QDialogButtonBox.StandardButton.Cancel)
        buttons.accepted.connect(self._on_save)
        buttons.rejected.connect(self.reject)
        self.save_button = buttons.button(QDialogButtonBox.StandardButton.Save)
        self.cancel_button = buttons.button(QDialogButtonBox.StandardButton.Cancel)
        layout.addWidget(buttons)

    def _build_input(self, field: FieldSpec) -> QWidget:
        value = self.initial_values.get(field.key)

        if field.type == "textarea":
            widget = QTextEdit()
            widget.setPlainText("" if value is None else str(value))
            widget.setPlaceholderText(field.placeholder)
            widget.setFixedHeight(field.height)
            return widget

        if field.type == "checkbox":
            widget = QCheckBox()
            widget.setChecked(bool(value) if value is not None else False)
            return widget

        if field.type == "combo":
            widget = QComboBox()
            for opt_label, opt_value in field.options:
                widget.addItem(opt_label, opt_value)
            if value is not None:
                idx = widget.findData(value)
                if idx >= 0:
                    widget.setCurrentIndex(idx)
            return widget

        if field.type == "date":
            return DatePickerField(value)

        if field.type == "image":
            return ImagePickerField(value)

        if field.type == "media":
            media_type = self.initial_values.get(field.type_key) if field.type_key else None
            return MediaPickerField(value, media_type=media_type, allow_video=field.allow_video)

        if field.type == "int":
            widget = QSpinBox()
            widget.setRange(int(field.minimum), int(field.maximum))
            widget.setValue(int(value) if value is not None else 0)
            return widget

        if field.type == "float":
            widget = QDoubleSpinBox()
            widget.setRange(field.minimum, field.maximum)
            widget.setDecimals(field.decimals)
            widget.setValue(float(value) if value is not None else 0.0)
            return widget

        widget = QLineEdit()
        widget.setPlaceholderText(field.placeholder)
        widget.setText("" if value is None else str(value))
        return widget

    def _set_widget_value(self, field: FieldSpec, value):
        # Reused by both initial population (_build_input, above) and the
        # voice-fill apply step below - one place that knows how to push
        # a plain value into each widget type.
        widget = self._inputs[field.key]
        if field.type == "textarea":
            widget.setPlainText(str(value))
        elif field.type in ("checkbox", "media"):
            pass  # never voice-filled - see _toggle_voice's field filter
        elif field.type == "combo":
            # Voice transcription is imperfect, so this matches loosely
            # (spoken text appearing anywhere in an option's label) rather
            # than requiring an exact match.
            idx = widget.findText(str(value), Qt.MatchFlag.MatchContains)
            if idx < 0:
                idx = widget.findData(value)
            if idx >= 0:
                widget.setCurrentIndex(idx)
        elif field.type in ("date", "image"):
            widget.set_value(value)
        elif field.type == "int":
            widget.setValue(int(value))
        elif field.type == "float":
            widget.setValue(float(value))
        else:
            widget.setText(str(value))

    def _toggle_voice(self):
        if self._voice_listening:
            if self._voice_recognizer:
                self._voice_recognizer.stop()
            return

        self._voice_listening = True
        self.voice_btn.setText("Stop Listening")
        self.voice_btn.setIcon(icon("stop"))
        self.voice_status_label.setText("Listening... say each field's label, then its value.")
        self.save_button.setEnabled(False)

        self._voice_recognizer = VoiceRecognizer()

        def work():
            return self._voice_recognizer.listen()

        def on_done(transcript: str):
            self._voice_listening = False
            self.voice_btn.setText("Voice Fill")
            self.voice_btn.setIcon(icon("mic"))
            self.save_button.setEnabled(True)
            if not transcript:
                self.voice_status_label.setText("Didn't catch anything - try again.")
                return
            values = parse_voice_fields(transcript, self.fields)
            for field in self.fields:
                if field.key in values:
                    self._set_widget_value(field, values[field.key])
            filled = ", ".join(f.label for f in self.fields if f.key in values)
            self.voice_status_label.setText(
                f'Heard: "{transcript}"' + (f" - filled: {filled}" if filled else " - nothing matched a field")
            )

        def on_error(message: str):
            self._voice_listening = False
            self.voice_btn.setText("Voice Fill")
            self.voice_btn.setIcon(icon("mic"))
            self.save_button.setEnabled(True)
            self.voice_status_label.setText(f"Voice input error: {message}")

        worker = Worker(work)
        worker.signals.finished.connect(on_done)
        worker.signals.error.connect(on_error)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)

    def _collect_values(self) -> Optional[dict]:
        values = {}
        for field in self.fields:
            widget = self._inputs[field.key]
            if field.type == "textarea":
                text = widget.toPlainText().strip()
                values[field.key] = text or None
            elif field.type == "checkbox":
                values[field.key] = widget.isChecked()
            elif field.type == "combo":
                values[field.key] = widget.currentData()
            elif field.type in ("date", "image"):
                values[field.key] = widget.value()
            elif field.type == "media":
                # A new file is uploaded in _on_save; until then the media
                # already saved (or "" once removed) stands here.
                values[field.key] = widget.current_key()
                if field.type_key:
                    values[field.type_key] = widget.current_type()
            elif field.type == "int":
                # For an optional int field (e.g. an FK id like Trees ID),
                # a QSpinBox can't represent "empty" - 0 stands in for it
                # and is sent as null rather than a literal 0.
                raw = widget.value()
                values[field.key] = None if (raw == 0 and not field.required) else raw
            elif field.type == "float":
                values[field.key] = widget.value()
            else:
                text = widget.text().strip()
                values[field.key] = text or None

            if field.required and (values[field.key] is None or values[field.key] == ""):
                self.error_label.setText(f"{field.label} is required.")
                return None

        return values

    def _on_save(self):
        values = self._collect_values()
        if values is None:
            return

        self.error_label.setText("")
        self._set_saving(True)
        uploads = [
            (field, self._inputs[field.key].pending())
            for field in self.fields
            if field.type == "media" and field.upload and self._inputs[field.key].pending()
        ]

        def work():
            # Files chosen on this computer go up first; the record is saved
            # with the keys they get. If an upload fails, nothing is saved.
            for field, pending in uploads:
                # Kept with the file, so pressing Save again after a refused
                # save doesn't upload it twice.
                if pending.uploaded is None:
                    pending.uploaded = field.upload(pending, values) or {}
                result = pending.uploaded
                values[field.key] = result["key"]
                if field.type_key:
                    values[field.type_key] = result.get("mediaType") or ("VIDEO" if pending.kind == "video" else "IMAGE")
            return self.save_fn(values)

        def on_done(_result):
            self._set_saving(False)
            self.accept()

        def on_error(message: str):
            self._set_saving(False)
            self.error_label.setText(message)

        worker = Worker(work)
        worker.signals.finished.connect(on_done)
        worker.signals.error.connect(on_error)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)

    def _set_saving(self, saving: bool):
        set_busy(self.save_button, saving, "Saving")
        self.saving_bar.setVisible(saving)
        self.cancel_button.setEnabled(not saving)
        for widget in self._inputs.values():
            widget.setEnabled(not saving)
