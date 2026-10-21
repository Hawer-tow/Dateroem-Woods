import base64
import html

from PySide6.QtCore import QSize, Qt, QThreadPool
from PySide6.QtGui import QColor, QIcon, QImage, QPainter, QPixmap
from PySide6.QtWidgets import (
    QAbstractItemView,
    QDialog,
    QFileDialog,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QMessageBox,
    QPushButton,
    QVBoxLayout,
)

from app.core import http, server_address
from app.core.worker import Worker
from app.ui import theme
from app.ui.widgets.busy import set_busy
from app.ui.widgets.media_picker import load_photo, photo_jpeg

# Same limits as the backend (ShowcaseService) - checked there too.
MAX_PICTURES = 12
MAX_CAPTION = 120
THUMB = QSize(192, 108)

def _list_style() -> str:
    """The picture list's style in the theme chosen now. (It used to be built
    once when the app started - always Forest - so in Light or Dark the list
    stayed green; 21 October 2026.)"""
    return f"""
QListWidget {{
    background-color: {theme.LAYER if theme.MODERN else theme.SURFACE};
    border: 1px solid {theme.BORDER};
    border-radius: {10 if theme.MODERN else 8}px;
    padding: 6px;
}}
QListWidget::item {{
    color: {theme.TEXT};
    padding: 6px;
    margin: 2px 0;
    border: 2px solid transparent;
    border-radius: 8px;
}}
QListWidget::item:selected {{
    color: {theme.WOOD_ON};
    font-weight: 600;
}}
QListWidget::item:hover:!selected {{
    background-color: {theme.SURFACE_ALT};
}}
"""


def _thumbnail(pixmap: QPixmap) -> QPixmap:
    """The whole picture on a dark 16:9 card, upright or not - like the
    website's slideshow frame - so every caption in the list lines up."""
    card = QPixmap(THUMB)
    card.fill(QColor(theme.BACKGROUND))
    scaled = pixmap.scaled(THUMB, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation)
    painter = QPainter(card)
    painter.drawPixmap((THUMB.width() - scaled.width()) // 2, (THUMB.height() - scaled.height()) // 2, scaled)
    painter.end()
    return card


def _jpeg_base64(image: QImage) -> str:
    # Shrunk here before sending (the admin server has no image library):
    # the same 1600 px / quality 85 the mobile server gives a phone's picture.
    return base64.b64encode(photo_jpeg(image)).decode("ascii")


class ShowcaseDialog(QDialog):
    """"pictures that can be used to show case what bulk orders look like" -
    the slideshow on the website's Bulk Orders page. These are not products:
    just example photos, added, captioned, put in order and removed here (or
    from the phone app). Every change shows on the website straight away."""

    def __init__(self, api_client, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self._active_workers = []
        self._generation = 0
        self._loaded = False
        self.pictures: list[dict] = []
        self.setWindowTitle("Bulk order pictures")
        self.setMinimumSize(620, 600)

        layout = QVBoxLayout(self)
        intro = QLabel(
            "These pictures play as a slideshow on the website's Bulk Orders page, so customers can see what a "
            "bulk order looks like. They are examples only - not products. The first picture shows first."
        )
        intro.setWordWrap(True)
        intro.setProperty("role", "muted")
        layout.addWidget(intro)

        self.list = QListWidget()
        self.list.setStyleSheet(_list_style())
        self.list.setIconSize(THUMB)
        self.list.setWordWrap(True)
        self.list.setSelectionMode(QAbstractItemView.SelectionMode.SingleSelection)
        self.list.itemSelectionChanged.connect(self._update_buttons)
        self.list.itemDoubleClicked.connect(lambda _item: self._edit_caption())
        layout.addWidget(self.list, 1)

        self.count_label = QLabel("Loading pictures...")
        self.count_label.setProperty("role", "muted")
        layout.addWidget(self.count_label)

        self.error_label = QLabel("")
        self.error_label.setProperty("role", "error")
        self.error_label.setWordWrap(True)
        self.error_label.setTextFormat(Qt.TextFormat.PlainText)
        layout.addWidget(self.error_label)

        row = QHBoxLayout()
        self.add_btn = QPushButton("Add picture...")
        self.add_btn.setProperty("role", "primary")
        self.add_btn.setEnabled(False)
        self.add_btn.clicked.connect(self._add)
        row.addWidget(self.add_btn)
        self.caption_btn = QPushButton("Edit caption...")
        self.caption_btn.clicked.connect(self._edit_caption)
        row.addWidget(self.caption_btn)
        self.up_btn = QPushButton("Move up")
        self.up_btn.clicked.connect(lambda: self._move(-1))
        row.addWidget(self.up_btn)
        self.down_btn = QPushButton("Move down")
        self.down_btn.clicked.connect(lambda: self._move(1))
        row.addWidget(self.down_btn)
        # Room between the everyday buttons and Remove, as in the sections.
        row.addSpacing(28)
        self.remove_btn = QPushButton("Remove")
        self.remove_btn.setProperty("role", "danger")
        self.remove_btn.clicked.connect(self._remove)
        row.addWidget(self.remove_btn)
        row.addStretch()
        self.close_btn = QPushButton("Close")
        self.close_btn.clicked.connect(self.accept)
        row.addWidget(self.close_btn)
        layout.addLayout(row)

        self._update_buttons()
        self._load()

    def _run(self, fn, on_done, on_error):
        worker = Worker(fn)
        worker.signals.finished.connect(on_done)
        worker.signals.error.connect(on_error)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)

    # --- loading -----------------------------------------------------------

    def _load(self, select_id: int | None = None):
        self.error_label.setText("")

        def done(rows):
            self._show(rows or [], select_id)

        def failed(message: str):
            self.count_label.setText("")
            self.error_label.setText(f"Couldn't load the pictures: {message}")

        self._run(lambda: self.api_client.get("/showcase"), done, failed)

    def _show(self, rows: list[dict], select_id: int | None):
        self._generation += 1
        generation = self._generation
        self._loaded = True
        self.pictures = rows
        self.list.clear()
        placeholder = QPixmap(THUMB)
        placeholder.fill(Qt.GlobalColor.transparent)
        for position, picture in enumerate(rows, start=1):
            item = QListWidgetItem(QIcon(placeholder), self._label(position, picture))
            item.setSizeHint(QSize(0, THUMB.height() + 20))
            self.list.addItem(item)
            if picture.get("image"):
                self._load_thumbnail(generation, position - 1, picture["image"])
        if select_id is not None:
            for i, picture in enumerate(rows):
                if picture.get("showcaseId") == select_id:
                    self.list.setCurrentRow(i)
        count = len(rows)
        if count == 0:
            self.count_label.setText("No pictures yet - the Bulk Orders page shows no slideshow until you add one.")
        elif count >= MAX_PICTURES:
            self.count_label.setText(f"{count} of {MAX_PICTURES} pictures - remove one to add another.")
        else:
            self.count_label.setText(f"{count} of {MAX_PICTURES} pictures")
        self._update_buttons()

    @staticmethod
    def _label(position: int, picture: dict) -> str:
        caption = (picture.get("caption") or "").strip()
        return f"{position}.  {caption}" if caption else f"{position}.  (no caption)"

    def _load_thumbnail(self, generation: int, row: int, image: str):
        url = server_address.absolute(image)

        def work():
            response = http.session().get(url, timeout=15)
            response.raise_for_status()
            return response.content

        def done(content: bytes):
            if generation != self._generation or row >= self.list.count():
                return
            pixmap = QPixmap()
            if pixmap.loadFromData(content):
                card = _thumbnail(pixmap)
                icon = QIcon(card)
                # As it is when chosen too - not tinted blue by Qt.
                icon.addPixmap(card, QIcon.Mode.Selected)
                self.list.item(row).setIcon(icon)

        # A picture that can't be fetched just keeps its blank square.
        self._run(work, done, lambda _message: None)

    # --- buttons -----------------------------------------------------------

    def _row(self) -> int:
        """The highlighted picture's row, or -1. Qt can make the first row
        "current" just because the list got the focus, without highlighting
        it - only a picture that looks chosen counts."""
        items = self.list.selectedItems()
        row = self.list.row(items[0]) if items else -1
        return row if 0 <= row < len(self.pictures) else -1

    def _selected(self) -> dict | None:
        row = self._row()
        return self.pictures[row] if row >= 0 else None

    def _update_buttons(self, *_):
        row = self._row()
        chosen = row >= 0
        self.add_btn.setEnabled(self._loaded and len(self.pictures) < MAX_PICTURES)
        self.caption_btn.setEnabled(chosen)
        self.up_btn.setEnabled(chosen and row > 0)
        self.down_btn.setEnabled(chosen and row < len(self.pictures) - 1)
        self.remove_btn.setEnabled(chosen)

    def _failed(self, button: QPushButton):
        def show(message: str):
            set_busy(button, False)
            self._update_buttons()
            self.error_label.setText(message)

        return show

    def _add(self):
        path, _filter = QFileDialog.getOpenFileName(
            self, "Choose a bulk order picture", "", "Images (*.jpg *.jpeg *.png *.webp *.bmp)"
        )
        if not path:
            return
        # Turned upright first (a phone photo copied to the computer often
        # stores its turn separately).
        image = load_photo(path)
        if image.isNull():
            QMessageBox.warning(self, "Couldn't read that picture", "Choose a JPEG, PNG, WebP or BMP picture.")
            return
        editor = CaptionDialog("New bulk order picture", "Add picture", image=image, parent=self)
        if not editor.exec():
            return
        caption = editor.caption()
        self.error_label.setText("")
        set_busy(self.add_btn, True, "Adding")

        def work():
            key = self.api_client.post(
                "/showcase/upload", json={"name": caption or "bulk-order", "dataBase64": _jpeg_base64(image)}, timeout=90
            )["key"]
            return self.api_client.post("/showcase", json={"image": key, "caption": caption}, actor=True)

        def done(created):
            set_busy(self.add_btn, False)
            self._load(select_id=(created or {}).get("showcaseId"))

        self._run(work, done, self._failed(self.add_btn))

    def _edit_caption(self):
        picture = self._selected()
        if not picture:
            return
        icon = self.list.item(self._row()).icon()
        editor = CaptionDialog(
            "Edit caption", "Save", caption=picture.get("caption") or "",
            pixmap=icon.pixmap(THUMB) if not icon.isNull() else None, parent=self,
        )
        if not editor.exec():
            return
        showcase_id = picture.get("showcaseId")
        self.error_label.setText("")
        set_busy(self.caption_btn, True, "Saving")

        def done(_result):
            set_busy(self.caption_btn, False)
            self._load(select_id=showcase_id)

        self._run(
            lambda: self.api_client.patch(f"/showcase/{showcase_id}", json={"caption": editor.caption()}, actor=True),
            done,
            self._failed(self.caption_btn),
        )

    def _move(self, step: int):
        row = self._row()
        target = row + step
        if not (row >= 0 and 0 <= target < len(self.pictures)):
            return
        ids = [picture.get("showcaseId") for picture in self.pictures]
        ids[row], ids[target] = ids[target], ids[row]
        moved_id = ids[target]
        button = self.up_btn if step < 0 else self.down_btn
        self.error_label.setText("")
        set_busy(button, True, "Moving")

        def done(_result):
            set_busy(button, False)
            self._load(select_id=moved_id)

        def failed(message: str):
            self._failed(button)(message)
            # Someone else changed the pictures meanwhile - show them as they are now.
            self._load(select_id=moved_id)

        self._run(lambda: self.api_client.put("/showcase/order", json={"showcaseIds": ids}, actor=True), done, failed)

    def _remove(self):
        picture = self._selected()
        if not picture:
            return
        caption = picture.get("caption")
        which = f"'{caption}'" if caption else f"picture {self._row() + 1}"
        # Written as rich text with the caption escaped: Qt would otherwise
        # treat a caption that looks like HTML as formatting.
        answer = QMessageBox.question(
            self, "Remove picture?",
            f"<p>Remove {html.escape(which)} from the Bulk Orders slideshow?</p><p>Customers stop seeing it straight away.</p>",
        )
        if answer != QMessageBox.StandardButton.Yes:
            return
        showcase_id = picture.get("showcaseId")
        self.error_label.setText("")
        set_busy(self.remove_btn, True, "Removing")

        def done(_result):
            set_busy(self.remove_btn, False)
            self._load()

        self._run(lambda: self.api_client.delete(f"/showcase/{showcase_id}", actor=True), done, self._failed(self.remove_btn))


class CaptionDialog(QDialog):
    """The picture with its optional caption - for a new picture, and for
    changing the caption of one already showing."""

    def __init__(self, title: str, ok_text: str, image: QImage | None = None, pixmap: QPixmap | None = None,
                 caption: str = "", parent=None):
        super().__init__(parent)
        self.setWindowTitle(title)
        self.setMinimumWidth(520)
        layout = QVBoxLayout(self)

        preview = QLabel()
        preview.setAlignment(Qt.AlignmentFlag.AlignCenter)
        if image is not None and not image.isNull():
            pixmap = QPixmap.fromImage(image)
        if pixmap is not None and not pixmap.isNull():
            preview.setPixmap(pixmap.scaled(480, 270, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation))
            layout.addWidget(preview)

        label = QLabel("Caption (optional)")
        layout.addWidget(label)
        self.field = QLineEdit(caption)
        self.field.setMaxLength(MAX_CAPTION)
        self.field.setPlaceholderText("e.g. 300 eucalyptus poles delivered to a site in Thika")
        layout.addWidget(self.field)
        self.counter = QLabel("")
        self.counter.setProperty("role", "muted")
        layout.addWidget(self.counter)
        self.field.textChanged.connect(self._count)
        self._count()

        row = QHBoxLayout()
        row.addStretch()
        cancel = QPushButton("Cancel")
        cancel.clicked.connect(self.reject)
        row.addWidget(cancel)
        ok = QPushButton(ok_text)
        ok.setProperty("role", "primary")
        ok.setDefault(True)
        ok.clicked.connect(self.accept)
        row.addWidget(ok)
        layout.addLayout(row)

    def _count(self, *_):
        self.counter.setText(f"{len(self.field.text())} / {MAX_CAPTION} - shown over the picture on the website")

    def caption(self) -> str | None:
        text = " ".join(self.field.text().split())
        return text or None
