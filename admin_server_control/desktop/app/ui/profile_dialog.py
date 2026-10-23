"""Profile, from the button above Settings (23 October 2026).

"a profile area ... all connected - if one updates on the phone the website
and desktop show the same": the signed-in admin's photo, name, email and
password, saved with the account (migration 039). The email and phone show
partly until Show is pressed. The phone number receives payouts, so it is
changed in Users > Edit, never here.
"""
from PySide6.QtCore import QBuffer, QByteArray, QIODevice, QRectF, Qt, QThreadPool, QTimer, Signal
from PySide6.QtGui import QColor, QImage, QPainter, QPainterPath, QPixmap
from PySide6.QtWidgets import (
    QDialog,
    QFileDialog,
    QFrame,
    QGridLayout,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QPushButton,
    QVBoxLayout,
)

from app.core import http, server_address
from app.core.api_client import ApiError, readable
from app.core.worker import Worker
from app.ui import theme

PHOTO_SIZE = 512


def mask_phone(phone: str | None) -> str:
    digits = "".join(c for c in (phone or "") if c.isdigit())
    return f"{digits[:2]}•• ••• {digits[-3:]}" if len(digits) >= 6 else ("•••" if phone else "Not given")


def mask_email(email: str | None) -> str:
    if not email or "@" not in email:
        return email or "Not given"
    name, domain = email.split("@", 1)
    return f"{name[:2]}•••@{domain}"


def round_photo(pixmap: QPixmap | None, size: int, initial: str = "?") -> QPixmap:
    """The photo in a wood-ringed circle, or the first letter of the name."""
    out = QPixmap(size, size)
    out.fill(Qt.GlobalColor.transparent)
    p = QPainter(out)
    p.setRenderHint(QPainter.RenderHint.Antialiasing)
    p.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform)
    circle = QPainterPath()
    circle.addEllipse(QRectF(2, 2, size - 4, size - 4))
    if pixmap is not None and not pixmap.isNull():
        p.setClipPath(circle)
        scaled = pixmap.scaled(size, size, Qt.AspectRatioMode.KeepAspectRatioByExpanding, Qt.TransformationMode.SmoothTransformation)
        p.drawPixmap((size - scaled.width()) // 2, (size - scaled.height()) // 2, scaled)
        p.setClipping(False)
    else:
        background = QColor(theme.TEXT)
        background.setAlpha(28)
        p.fillPath(circle, background)
        font = p.font()
        font.setPixelSize(int(size * 0.42))
        font.setBold(True)
        p.setFont(font)
        p.setPen(QColor(theme.TEXT))
        p.drawText(QRectF(0, 0, size, size), Qt.AlignmentFlag.AlignCenter, (initial or "?")[:1].upper())
    pen = p.pen()
    pen.setColor(QColor(theme.ACCENT))
    pen.setWidthF(max(2.0, size / 40))
    p.setPen(pen)
    p.setBrush(Qt.BrushStyle.NoBrush)
    p.drawEllipse(QRectF(2, 2, size - 4, size - 4))
    p.end()
    return out


def square_jpeg(path: str) -> bytes | None:
    """The centre of the chosen picture as a 512 px square JPEG."""
    image = QImage(path)
    if image.isNull():
        return None
    side = min(image.width(), image.height())
    square = image.copy((image.width() - side) // 2, (image.height() - side) // 2, side, side)
    square = square.scaled(PHOTO_SIZE, PHOTO_SIZE, Qt.AspectRatioMode.IgnoreAspectRatio, Qt.TransformationMode.SmoothTransformation)
    data = QByteArray()
    buffer = QBuffer(data)
    buffer.open(QIODevice.OpenModeFlag.WriteOnly)
    square.convertToFormat(QImage.Format.Format_RGB32).save(buffer, "JPG", 85)
    buffer.close()
    return bytes(data)


class ProfileDialog(QDialog):
    # The main window follows each save at once: the menu photo, the status
    # bar and the Dashboard greeting (QPixmap, or None once removed).
    photo_saved = Signal(object)
    name_saved = Signal(str)

    def __init__(self, api_client, session, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.session = session
        self.me: dict = {}
        self.photo: QPixmap | None = None
        self.photo_changed = False
        self._workers = []
        self.setWindowTitle("Profile")
        self.setMinimumWidth(620)

        # Photo, name, username and role.
        head = QFrame()
        head.setProperty("role", "card")
        head_row = QHBoxLayout(head)
        head_row.setContentsMargins(22, 18, 22, 18)
        head_row.setSpacing(20)
        self.avatar = QLabel()
        self.avatar.setFixedSize(108, 108)
        self.avatar.setPixmap(round_photo(None, 108, session.name or "?"))
        head_row.addWidget(self.avatar)
        who = QVBoxLayout()
        self.name_title = QLabel(session.name or "")
        self.name_title.setProperty("role", "title")
        who.addWidget(self.name_title)
        self.who_line = QLabel(f"@{session.user_name}  ·  {session.role_name}")
        self.who_line.setProperty("role", "muted")
        who.addWidget(self.who_line)
        photo_row = QHBoxLayout()
        self.choose_btn = QPushButton("Choose photo...")
        self.choose_btn.clicked.connect(self._choose_photo)
        photo_row.addWidget(self.choose_btn)
        self.remove_btn = QPushButton("Remove photo")
        self.remove_btn.clicked.connect(self._remove_photo)
        self.remove_btn.setVisible(False)
        photo_row.addWidget(self.remove_btn)
        photo_row.addStretch(1)
        who.addLayout(photo_row)
        head_row.addLayout(who, 1)

        # Name and email.
        details = QFrame()
        details.setProperty("role", "card")
        grid = QGridLayout(details)
        grid.setContentsMargins(22, 16, 22, 18)
        grid.setHorizontalSpacing(14)
        grid.setVerticalSpacing(8)
        title = QLabel("Your details")
        title.setProperty("role", "title")
        grid.addWidget(title, 0, 0, 1, 3)
        grid.addWidget(QLabel("Name"), 1, 0)
        self.name_input = QLineEdit()
        grid.addWidget(self.name_input, 1, 1, 1, 2)
        grid.addWidget(QLabel("Email"), 2, 0)
        self.email_input = QLineEdit()
        self.email_input.setPlaceholderText("Not given")
        self.email_input.setEchoMode(QLineEdit.EchoMode.Password)
        self.email_input.textChanged.connect(self._email_edited)
        grid.addWidget(self.email_input, 2, 1)
        self.email_show = QPushButton("Show")
        self.email_show.setCheckable(True)
        self.email_show.toggled.connect(
            lambda on: (self.email_input.setEchoMode(QLineEdit.EchoMode.Normal if on else QLineEdit.EchoMode.Password),
                        self.email_show.setText("Hide" if on else "Show"))
        )
        grid.addWidget(self.email_show, 2, 2)
        self.password_caption = QLabel("Your password")
        grid.addWidget(self.password_caption, 3, 0)
        self.password_input = QLineEdit()
        self.password_input.setEchoMode(QLineEdit.EchoMode.Password)
        self.password_input.setPlaceholderText("Needed to change the email")
        grid.addWidget(self.password_input, 3, 1, 1, 2)
        self.password_caption.setVisible(False)
        self.password_input.setVisible(False)
        grid.addWidget(QLabel("Phone"), 4, 0)
        self.phone_label = QLabel("")
        grid.addWidget(self.phone_label, 4, 1)
        self.phone_show = QPushButton("Show")
        self.phone_show.setCheckable(True)
        self.phone_show.toggled.connect(self._show_phone)
        grid.addWidget(self.phone_show, 4, 2)
        phone_note = QLabel("The phone number receives payouts, so it is changed in Users > Edit.")
        phone_note.setProperty("role", "muted")
        phone_note.setWordWrap(True)
        grid.addWidget(phone_note, 5, 1, 1, 2)
        save_row = QHBoxLayout()
        save_row.addStretch(1)
        self.save_btn = QPushButton("Save details")
        self.save_btn.setProperty("role", "primary")
        self.save_btn.clicked.connect(self._save_details)
        save_row.addWidget(self.save_btn)
        grid.addLayout(save_row, 6, 0, 1, 3)
        grid.setColumnStretch(1, 1)

        # Password.
        security = QFrame()
        security.setProperty("role", "card")
        sec = QHBoxLayout(security)
        sec.setContentsMargins(22, 14, 22, 14)
        sec_text = QVBoxLayout()
        sec_title = QLabel("Password")
        sec_title.setProperty("role", "title")
        sec_text.addWidget(sec_title)
        sec_hint = QLabel("Changing it signs this account out on its other computers and phones.")
        sec_hint.setProperty("role", "muted")
        sec_hint.setWordWrap(True)
        sec_text.addWidget(sec_hint)
        sec.addLayout(sec_text, 1)
        password_btn = QPushButton("Change password...")
        password_btn.clicked.connect(self._change_password)
        sec.addWidget(password_btn)

        self.message = QLabel("")
        self.message.setWordWrap(True)
        self.message.setProperty("role", "error")

        close = QPushButton("Close")
        close.clicked.connect(self.accept)
        buttons = QHBoxLayout()
        buttons.addStretch(1)
        buttons.addWidget(close)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(18, 18, 18, 16)
        layout.setSpacing(12)
        layout.addWidget(head)
        layout.addWidget(details)
        layout.addWidget(security)
        layout.addWidget(self.message)
        layout.addLayout(buttons)

        self._run(lambda: self.api_client.get("/auth/me"), self._loaded, self._failed)

    # -- loading ----------------------------------------------------------
    def _run(self, fn, on_done, on_error):
        worker = Worker(fn)
        worker.signals.finished.connect(on_done)
        worker.signals.error.connect(on_error)
        self._workers.append(worker)
        QThreadPool.globalInstance().start(worker)

    def _say(self, text: str, error: bool = True, busy: bool = False):
        self.message.setProperty("role", "muted" if busy else "error" if error else "success")
        self.message.setText(text)
        self.message.style().unpolish(self.message)
        self.message.style().polish(self.message)

    def _failed(self, message: str):
        self._say(readable(message))

    def _loaded(self, me: dict):
        self.me = me or {}
        self.name_input.setText(self.me.get("name") or "")
        self.email_input.blockSignals(True)
        self.email_input.setText(self.me.get("email") or "")
        self.email_input.blockSignals(False)
        self._show_phone(self.phone_show.isChecked())
        self.remove_btn.setVisible(bool(self.me.get("photoUrl")))
        if self.me.get("photoUrl"):
            self._fetch_photo(self.me["photoUrl"])

    def _fetch_photo(self, url: str):
        def work():
            response = http.session().get(server_address.absolute(url), timeout=15)
            response.raise_for_status()
            return response.content

        def done(content: bytes):
            pixmap = QPixmap()
            if pixmap.loadFromData(content):
                self.photo = pixmap
                self.avatar.setPixmap(round_photo(pixmap, 108))

        self._run(work, done, lambda _m: None)

    def _show_phone(self, shown: bool):
        phone = self.me.get("phoneNo")
        self.phone_label.setText((phone or "Not given") if shown else mask_phone(phone))
        self.phone_show.setText("Hide" if shown else "Show")

    def _email_edited(self, text: str):
        changed = text.strip().lower() != (self.me.get("email") or "").lower() and bool(self.me.get("hasPassword"))
        self.password_caption.setVisible(changed)
        self.password_input.setVisible(changed)

    # -- saving -----------------------------------------------------------
    def _save_details(self):
        body = {
            "name": self.name_input.text(),
            "email": self.email_input.text().strip() or None,
            "currentPassword": self.password_input.text() or None,
        }
        self.save_btn.setEnabled(False)
        self.save_btn.setText("Saving...")
        self._say("Saving your details...", busy=True)

        def done(result):
            result = result or {}
            self.session.name = result.get("name") or self.session.name
            self.name_title.setText(self.session.name)
            self.me["name"] = self.session.name
            self.me["email"] = result.get("email")
            self.password_input.clear()
            self._email_edited(self.email_input.text())
            self.name_saved.emit(self.session.name)
            self._say("Your details are saved - your name shows in the app now.", error=False)
            self.save_btn.setText("Saved")
            QTimer.singleShot(2500, self._reset_save_button)

        def failed(message: str):
            self._reset_save_button()
            self._say(readable(message))

        self._run(lambda: self.api_client.put("/auth/me/profile", json=body), done, failed)

    def _reset_save_button(self):
        self.save_btn.setEnabled(True)
        self.save_btn.setText("Save details")

    def _choose_photo(self):
        path, _ = QFileDialog.getOpenFileName(self, "Choose a photo", "", "Pictures (*.jpg *.jpeg *.png *.webp *.bmp)")
        if not path:
            return
        data = square_jpeg(path)
        if data is None:
            self._say("That file can't be read as a picture.")
            return
        # The photo is saved as soon as it is chosen (Save details is for
        # the name and email) - show it at once and say so.
        pixmap = QPixmap()
        pixmap.loadFromData(data)
        self.avatar.setPixmap(round_photo(pixmap, 108))
        self.choose_btn.setEnabled(False)
        self.choose_btn.setText("Saving photo...")
        self._say("Saving your photo...", busy=True)

        def done(result):
            self.choose_btn.setEnabled(True)
            self.choose_btn.setText("Choose photo...")
            self.photo_changed = True
            self.photo = pixmap
            self.me["photoUrl"] = (result or {}).get("photoUrl")
            self.remove_btn.setVisible(True)
            self.photo_saved.emit(pixmap)
            self._say("Your photo is saved - it shows in the menu now.", error=False)

        def failed(message: str):
            self.choose_btn.setEnabled(True)
            self.choose_btn.setText("Choose photo...")
            self.avatar.setPixmap(round_photo(self.photo, 108, self.session.name or "?"))
            self._say(readable(message))

        self._run(lambda: self.api_client.post_file("/auth/me/photo", data, "image/jpeg", timeout=60), done, failed)

    def _remove_photo(self):
        try:
            self.api_client.delete("/auth/me/photo")
        except ApiError as exc:
            self._say(readable(str(exc.detail)))
            return
        self.photo = None
        self.photo_changed = True
        self.me["photoUrl"] = None
        self.avatar.setPixmap(round_photo(None, 108, self.session.name or "?"))
        self.remove_btn.setVisible(False)
        self.photo_saved.emit(None)
        self._say("Your photo is removed.", error=False)

    def _change_password(self):
        from app.ui.password_change_dialog import PasswordChangeDialog

        if PasswordChangeDialog(self.api_client, self.session, parent=self).exec():
            self._say("Password changed. Other computers and phones signed in to this account were signed out.", error=False)
