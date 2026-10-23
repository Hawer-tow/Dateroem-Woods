"""Settings, from the button above Sign Out (20 October 2026).

For now Appearance: "is it possible to add more themes like the dark and
light theme and what we have will be default ... when that user for that
account goes into app or the mobile the theme is still the one they choose
last". Picking a theme saves it with the account (so the website and the
phone app open in it too) and then main.py rebuilds the main window in it -
on the same section, without signing in again.
"""
from PySide6.QtCore import QRectF, Qt, Signal
from PySide6.QtGui import QColor, QPainter, QPen
from PySide6.QtWidgets import QDialog, QFrame, QHBoxLayout, QLabel, QPushButton, QSpinBox, QVBoxLayout, QWidget

from app.core.api_client import ApiError
from app.ui import theme


class _ThemeOption(QWidget):
    """A small picture of one theme - its page, a card, two lines of text and
    the wood - with its name; the chosen one is ringed in wood."""

    picked = Signal(str)

    def __init__(self, name: str, selected: bool, parent=None):
        super().__init__(parent)
        self.name = name
        self.selected = selected
        self.setFixedSize(150, 136)
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        self.setFocusPolicy(Qt.FocusPolicy.StrongFocus)
        self.setAccessibleName(f"{theme.THEME_LABELS[name]} theme" + (", chosen" if selected else ""))

    def mouseReleaseEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton and self.rect().contains(event.position().toPoint()):
            self.picked.emit(self.name)

    def keyPressEvent(self, event):
        if event.key() in (Qt.Key.Key_Return, Qt.Key.Key_Enter, Qt.Key.Key_Space):
            self.picked.emit(self.name)
        else:
            super().keyPressEvent(event)

    def paintEvent(self, _event):
        colours = theme.PALETTES[self.name]
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        frame = QRectF(self.rect()).adjusted(1.5, 1.5, -1.5, -1.5)
        ring = QColor(theme.ACCENT) if self.selected or self.hasFocus() else QColor(theme.TEXT_MUTED)
        if not (self.selected or self.hasFocus()):
            ring.setAlpha(70)
        p.setPen(QPen(ring, 2.4 if self.selected else 1.2))
        p.setBrush(Qt.BrushStyle.NoBrush)
        p.drawRoundedRect(frame, 12, 12)

        preview = QRectF(10, 10, self.width() - 20, 86)
        p.setPen(QPen(QColor(0, 0, 0, 50), 1))
        p.setBrush(QColor(colours["BACKGROUND"]))
        p.drawRoundedRect(preview, 8, 8)
        p.setPen(Qt.PenStyle.NoPen)
        p.setBrush(QColor(colours["SURFACE"]))
        p.drawRoundedRect(QRectF(preview.left() + 8, preview.top() + 8, preview.width() - 16, 30), 5, 5)
        line = QColor(colours["TEXT"])
        line.setAlpha(190)
        p.setBrush(line)
        p.drawRoundedRect(QRectF(preview.left() + 8, preview.top() + 46, preview.width() * 0.62, 7), 3, 3)
        muted = QColor(colours["TEXT_MUTED"])
        muted.setAlpha(160)
        p.setBrush(muted)
        p.drawRoundedRect(QRectF(preview.left() + 8, preview.top() + 59, preview.width() * 0.42, 7), 3, 3)
        p.setBrush(QColor(colours["ACCENT"]))
        p.drawEllipse(QRectF(preview.right() - 18, preview.bottom() - 18, 10, 10))

        font = p.font()
        font.setPointSizeF(10.5)
        font.setBold(self.selected)
        p.setFont(font)
        p.setPen(QColor(theme.TEXT))
        label = ("✓  " if self.selected else "") + theme.THEME_LABELS[self.name]
        p.drawText(QRectF(0, preview.bottom() + 6, self.width(), 28), Qt.AlignmentFlag.AlignCenter, label)
        p.end()


class SettingsDialog(QDialog):
    def __init__(self, api_client, session, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.session = session
        self.theme_changed = False
        self.setWindowTitle("Settings")
        self.setMinimumWidth(560)

        card = QFrame()
        card.setProperty("role", "card")
        card_layout = QVBoxLayout(card)
        card_layout.setContentsMargins(22, 18, 22, 20)
        card_layout.setSpacing(6)
        title = QLabel("Appearance")
        title.setProperty("role", "title")
        card_layout.addWidget(title)
        hint = QLabel("Saved with your account - the website and the phone app open in it too.")
        hint.setProperty("role", "muted")
        hint.setWordWrap(True)
        card_layout.addWidget(hint)
        card_layout.addSpacing(10)

        row = QHBoxLayout()
        row.setSpacing(14)
        for name in theme.PALETTES:
            option = _ThemeOption(name, selected=name == theme.CURRENT)
            option.picked.connect(self._pick)
            row.addWidget(option)
        row.addStretch(1)
        card_layout.addLayout(row)

        self.message = QLabel("")
        self.message.setProperty("role", "error")
        self.message.setWordWrap(True)
        card_layout.addWidget(self.message)
        note = QLabel("The sign-in screen keeps its forest colours.")
        note.setProperty("role", "muted")
        card_layout.addWidget(note)

        # "a timer like about 25 minutes of inactivity" (migration 039): the
        # SuperAdmin sets the minutes for every computer and phone; everyone
        # else sees them.
        lock_card = QFrame()
        lock_card.setProperty("role", "card")
        lock_layout = QVBoxLayout(lock_card)
        lock_layout.setContentsMargins(22, 16, 22, 18)
        lock_layout.setSpacing(6)
        lock_title = QLabel("Idle lock")
        lock_title.setProperty("role", "title")
        lock_layout.addWidget(lock_title)
        minutes = int(session.idle_lock_minutes or 25)
        if session.role_name == "SuperAdmin":
            lock_hint = QLabel(
                "After this many minutes without use the desktop app asks for the password and the phone app for the "
                "fingerprint. It applies to every computer and phone from their next sign-in (here, at once)."
            )
            lock_hint.setProperty("role", "muted")
            lock_hint.setWordWrap(True)
            lock_layout.addWidget(lock_hint)
            lock_row = QHBoxLayout()
            lock_row.addWidget(QLabel("Lock after"))
            self.lock_minutes = QSpinBox()
            self.lock_minutes.setRange(5, 120)
            self.lock_minutes.setSuffix(" minutes")
            self.lock_minutes.setValue(minutes)
            lock_row.addWidget(self.lock_minutes)
            save_lock = QPushButton("Save")
            save_lock.clicked.connect(self._save_lock_minutes)
            lock_row.addWidget(save_lock)
            lock_row.addStretch(1)
            lock_layout.addLayout(lock_row)
        else:
            lock_hint = QLabel(f"The app locks after {minutes} minutes without use and asks for your password. The SuperAdmin sets this.")
            lock_hint.setProperty("role", "muted")
            lock_hint.setWordWrap(True)
            lock_layout.addWidget(lock_hint)
        self.lock_message = QLabel("")
        self.lock_message.setProperty("role", "muted")
        lock_layout.addWidget(self.lock_message)

        close = QPushButton("Close")
        close.clicked.connect(self.reject)
        buttons = QHBoxLayout()
        buttons.addStretch(1)
        buttons.addWidget(close)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(18, 18, 18, 16)
        layout.addWidget(card)
        layout.addWidget(lock_card)
        layout.addLayout(buttons)

    def _save_lock_minutes(self):
        minutes = self.lock_minutes.value()
        try:
            self.api_client.put("/auth/idle-lock", json={"minutes": minutes})
        except ApiError as exc:
            self.lock_message.setText(f"Couldn't save it: {exc}")
            return
        self.session.idle_lock_minutes = minutes
        self.lock_message.setText(f"Saved: {minutes} minutes.")

    def _pick(self, name: str):
        if name == theme.CURRENT:
            return
        self.message.setText("")
        try:
            self.api_client.put("/auth/me/theme", json={"theme": name})
        except ApiError as exc:
            self.message.setText(f"Couldn't save the theme to your account: {exc}")
            return
        self.session.theme = name
        theme.set_theme(name)
        self.theme_changed = True
        self.accept()
