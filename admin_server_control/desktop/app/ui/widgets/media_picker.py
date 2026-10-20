"""Photo (or video) from this computer for the product and campaign forms.

"the ability to go to media and pick a new media or product when creating a
new product since i can be having the video or photo here in the laptop" -
the same way as the Bulk order pictures window: choose a file, see it, and
it is uploaded when the form is saved. A photo is turned upright (camera
rotation), shrunk to at most 1600 px and saved as JPEG here, like the phone
app does; a video (campaigns only) is sent as it is, up to 60 MB.
"""
from dataclasses import dataclass, field
from pathlib import Path

from PySide6.QtCore import QBuffer, QByteArray, QIODevice, QSize, Qt, QThreadPool, QUrl
from PySide6.QtGui import QColor, QDesktopServices, QImage, QImageReader, QPainter, QPixmap
from PySide6.QtWidgets import QFileDialog, QHBoxLayout, QLabel, QMessageBox, QPushButton, QSizePolicy, QVBoxLayout, QWidget

from app.core import http, server_address
from app.core.worker import Worker
from app.ui import theme
from app.ui.icons import pixmap as icon_pixmap

MAX_SIDE = 1600
JPEG_QUALITY = 85
VIDEO_EXTENSIONS = {".mp4", ".mov", ".webm", ".3gp", ".m4v"}
MAX_VIDEO_BYTES = 60 * 1024 * 1024
PHOTO_PATTERNS = "*.jpg *.jpeg *.png *.webp *.bmp"
PREVIEW = QSize(240, 135)


def load_photo(path: str) -> QImage:
    """The photo turned the right way up (phones often store the turn
    separately, which plain QImage(path) ignores)."""
    reader = QImageReader(path)
    reader.setAutoTransform(True)
    return reader.read()


def photo_jpeg(image: QImage) -> bytes:
    if max(image.width(), image.height()) > MAX_SIDE:
        image = image.scaled(MAX_SIDE, MAX_SIDE, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation)
    data = QByteArray()
    buffer = QBuffer(data)
    buffer.open(QIODevice.OpenModeFlag.WriteOnly)
    image.convertToFormat(QImage.Format.Format_RGB32).save(buffer, "JPEG", JPEG_QUALITY)
    return bytes(data)


@dataclass
class PendingMedia:
    """A file chosen on this computer, not uploaded yet."""
    path: str
    kind: str  # "image" or "video"
    image: QImage | None = field(default=None, repr=False)
    uploaded: dict | None = None  # the server's answer, once sent

    @property
    def extension(self) -> str:
        return Path(self.path).suffix.lower()

    def upload_body(self) -> tuple[bytes, str]:
        """(file bytes, content type) to send - safe to call off the UI thread."""
        if self.kind == "image":
            return photo_jpeg(self.image), "image/jpeg"
        return Path(self.path).read_bytes(), "application/octet-stream"


def _card(source: QPixmap | None, label_icon: str | None = None) -> QPixmap:
    """The whole picture (or an icon) on a 16:9 card a shade lighter than the form."""
    card = QPixmap(PREVIEW)
    card.fill(QColor(theme.SURFACE))
    painter = QPainter(card)
    if source is not None and not source.isNull():
        scaled = source.scaled(PREVIEW, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation)
        painter.drawPixmap((PREVIEW.width() - scaled.width()) // 2, (PREVIEW.height() - scaled.height()) // 2, scaled)
    elif label_icon:
        mark = icon_pixmap(label_icon, 44, theme.TEXT_MUTED)
        painter.drawPixmap((PREVIEW.width() - mark.width()) // 2, (PREVIEW.height() - mark.height()) // 2, mark)
    painter.end()
    return card


def _size_text(size: int) -> str:
    return f"{size / (1024 * 1024):.1f} MB" if size >= 1024 * 1024 else f"{max(1, size // 1024)} KB"


class MediaPickerField(QWidget):
    """value: the media already saved (its address from the server), or None.
    media_type: "IMAGE" or "VIDEO" for it. allow_video: campaigns."""

    def __init__(self, value=None, media_type=None, allow_video: bool = False, parent=None):
        super().__init__(parent)
        self.allow_video = allow_video
        self._existing = value or None
        self._existing_type = (media_type or "IMAGE") if self._existing else None
        self._pending: PendingMedia | None = None
        self._removed = False
        self._active_workers = []

        # Its own height only - a tall form doesn't stretch it into gaps.
        self.setSizePolicy(QSizePolicy.Policy.Preferred, QSizePolicy.Policy.Fixed)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(6)

        self.preview = QLabel()
        self.preview.setFixedSize(PREVIEW)
        self.preview.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(self.preview)

        self.info = QLabel("")
        self.info.setProperty("role", "muted")
        self.info.setWordWrap(True)
        self.info.setTextFormat(Qt.TextFormat.PlainText)
        layout.addWidget(self.info)

        row = QHBoxLayout()
        self.choose_button = QPushButton("Choose from this computer...")
        self.choose_button.clicked.connect(self._choose)
        row.addWidget(self.choose_button)
        self.view_button = QPushButton("View")
        self.view_button.setToolTip("Open it in this computer's photo or video viewer")
        self.view_button.clicked.connect(self._view)
        row.addWidget(self.view_button)
        self.remove_button = QPushButton("Remove")
        self.remove_button.clicked.connect(self._remove)
        row.addWidget(self.remove_button)
        row.addStretch()
        layout.addLayout(row)

        self._show()

    # --- what the form reads -------------------------------------------------

    def pending(self) -> PendingMedia | None:
        return self._pending

    def current_key(self):
        """What to save when nothing new is uploaded: the media already there,
        "" once removed (clears it), or None when there never was any."""
        if self._removed:
            return ""
        return self._existing

    def current_type(self):
        if self._removed or not self._existing:
            return None
        return self._existing_type

    def value(self):
        return self.current_key()

    # --- buttons -------------------------------------------------------------

    def _choose(self):
        if self.allow_video:
            videos = " ".join(f"*{ext}" for ext in sorted(VIDEO_EXTENSIONS))
            filters = f"Photos and videos ({PHOTO_PATTERNS} {videos});;Photos ({PHOTO_PATTERNS});;Videos ({videos})"
            title = "Choose a photo or video"
        else:
            filters = f"Photos ({PHOTO_PATTERNS})"
            title = "Choose a photo"
        path, _filter = QFileDialog.getOpenFileName(self, title, "", filters)
        if not path:
            return
        extension = Path(path).suffix.lower()
        if extension in VIDEO_EXTENSIONS:
            if not self.allow_video:
                QMessageBox.warning(self, "Photos only", "A product takes a photo, not a video.")
                return
            size = Path(path).stat().st_size
            if size > MAX_VIDEO_BYTES:
                QMessageBox.warning(
                    self, "Video too large",
                    f"This video is {_size_text(size)}; videos can be up to 60 MB. Choose a shorter one, or make it smaller first.",
                )
                return
            self._pending = PendingMedia(path, "video")
        else:
            image = load_photo(path)
            if image.isNull():
                QMessageBox.warning(self, "Couldn't read that photo", "Choose a JPEG, PNG, WebP or BMP photo.")
                return
            self._pending = PendingMedia(path, "image", image)
        self._removed = False
        self._show()

    def _view(self):
        if self._pending:
            QDesktopServices.openUrl(QUrl.fromLocalFile(self._pending.path))
        elif self._existing and not self._removed:
            QDesktopServices.openUrl(QUrl(server_address.absolute(self._existing)))

    def _remove(self):
        self._pending = None
        self._removed = bool(self._existing)
        self._show()

    # --- showing it ----------------------------------------------------------

    def _show(self):
        noun = "photo or video" if self.allow_video else "photo"
        if self._pending:
            name = Path(self._pending.path).name
            size = _size_text(Path(self._pending.path).stat().st_size)
            if self._pending.kind == "image":
                self.preview.setPixmap(_card(QPixmap.fromImage(self._pending.image)))
                image = self._pending.image
                self.info.setText(f"New photo: {name} ({image.width()} x {image.height()}, {size}) - uploaded when you save")
            else:
                self.preview.setPixmap(_card(None, "video"))
                self.info.setText(f"New video: {name} ({size}) - uploaded when you save")
            self.choose_button.setText("Choose another...")
        elif self._existing and not self._removed:
            kind = "video" if self._existing_type == "VIDEO" else "photo"
            self.info.setText(f"Current {kind}: {Path(self._existing).name}")
            self.choose_button.setText("Replace from this computer...")
            if kind == "video":
                self.preview.setPixmap(_card(None, "video"))
            else:
                self.preview.setPixmap(_card(None, "image"))
                self._load_existing()
        else:
            self.preview.setPixmap(_card(None, "image"))
            self.info.setText(f"{noun.capitalize()} will be removed when you save." if self._removed else f"No {noun} yet.")
            self.choose_button.setText("Choose from this computer...")
        has_something = bool(self._pending) or (bool(self._existing) and not self._removed)
        self.view_button.setEnabled(has_something)
        self.remove_button.setEnabled(has_something)

    def _load_existing(self):
        url = server_address.absolute(self._existing)
        shown = self._existing

        def work():
            response = http.session().get(url, timeout=15)
            response.raise_for_status()
            return response.content

        def done(content: bytes):
            # Only if it is still the one showing (not replaced or removed meanwhile).
            if self._pending or self._removed or self._existing != shown:
                return
            picture = QPixmap()
            if picture.loadFromData(content):
                self.preview.setPixmap(_card(picture))

        worker = Worker(work)
        worker.signals.finished.connect(done)
        worker.signals.error.connect(lambda _message: None)  # keeps the plain card
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)
