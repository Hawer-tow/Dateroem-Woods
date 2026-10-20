"""Photo field: choose an image file, see a preview, keep it (scaled down)
as a data URL. "add a photo instead of only scan with AI since an image
will still do fine" - the same picker backs a plain attached photo and the
photo an AI scan was run on."""
import base64

from PySide6.QtCore import QBuffer, QByteArray, QIODevice, Qt
from PySide6.QtGui import QImage, QPixmap
from PySide6.QtWidgets import QFileDialog, QHBoxLayout, QLabel, QPushButton, QVBoxLayout, QWidget

MAX_SIDE = 1400
JPEG_QUALITY = 80


def image_to_data_url(image: QImage) -> str:
    if max(image.width(), image.height()) > MAX_SIDE:
        image = image.scaled(MAX_SIDE, MAX_SIDE, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation)
    data = QByteArray()
    buffer = QBuffer(data)
    buffer.open(QIODevice.OpenModeFlag.WriteOnly)
    image.save(buffer, "JPEG", JPEG_QUALITY)
    return "data:image/jpeg;base64," + base64.b64encode(bytes(data)).decode("ascii")


def data_url_to_image(data_url: str) -> QImage:
    image = QImage()
    if data_url and "," in data_url:
        image.loadFromData(base64.b64decode(data_url.split(",", 1)[1]))
    return image


class ImagePickerField(QWidget):
    def __init__(self, value=None, parent=None):
        super().__init__(parent)
        self._data_url: str | None = None

        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)

        row = QHBoxLayout()
        self.choose_button = QPushButton("Choose photo...")
        self.choose_button.clicked.connect(self._choose)
        row.addWidget(self.choose_button)
        self.remove_button = QPushButton("Remove")
        self.remove_button.clicked.connect(lambda: self.set_value(None))
        row.addWidget(self.remove_button)
        row.addStretch()
        layout.addLayout(row)

        self.preview = QLabel("")
        self.preview.setAlignment(Qt.AlignmentFlag.AlignLeft)
        layout.addWidget(self.preview)

        self.set_value(value)

    def _choose(self):
        path, _filter = QFileDialog.getOpenFileName(self, "Choose a photo", "", "Images (*.png *.jpg *.jpeg *.webp *.bmp)")
        if not path:
            return
        image = QImage(path)
        if image.isNull():
            self.preview.setText("Couldn't read that image.")
            return
        self.set_value(image_to_data_url(image))

    def set_value(self, data_url):
        image = data_url_to_image(data_url) if data_url else QImage()
        if image.isNull():
            self._data_url = None
            self.preview.clear()
            self.preview.setVisible(False)
            self.remove_button.setEnabled(False)
            self.choose_button.setText("Choose photo...")
            return
        # Re-encoded so a photo that arrives already big (e.g. the one an AI
        # scan was run on) is stored at the same modest size as any other.
        self._data_url = image_to_data_url(image)
        self.preview.setPixmap(
            QPixmap.fromImage(image).scaled(220, 160, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation)
        )
        self.preview.setVisible(True)
        self.remove_button.setEnabled(True)
        self.choose_button.setText("Change photo...")

    def value(self):
        return self._data_url
