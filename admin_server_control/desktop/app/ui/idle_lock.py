"""The idle lock (23 October 2026, migration 039).

"i noticed that the desktop has a timer after inactive time ... can i place a
timer like about 25 minutes of inactivity" - after the minutes the SuperAdmin
set (Settings; every computer and phone, from its next sign-in) without a key
press or the mouse moving over the app, a lock covers the window and asks for
the password. A minute before, a small "Still there?" notice appears; any
movement puts it away. Nothing is lost: the screens stay as they were under the
lock, and the servers and anything uploading carry on.
"""
import time

from PySide6.QtCore import QEvent, QObject, QRectF, Qt, QTimer
from PySide6.QtGui import QColor, QPainter
from PySide6.QtWidgets import QApplication, QDialog, QFrame, QHBoxLayout, QLabel, QLineEdit, QPushButton, QVBoxLayout

from app.core.api_client import ApiError, readable
from app.ui import theme
from app.ui.icons import pixmap

ACTIVITY = {
    QEvent.Type.MouseMove, QEvent.Type.MouseButtonPress, QEvent.Type.KeyPress,
    QEvent.Type.Wheel, QEvent.Type.TouchBegin,
}
WARN_SECONDS = 60


class IdleLock(QObject):
    def __init__(self, window, api_client, session, on_sign_out):
        super().__init__(window)
        self.window = window
        self.api_client = api_client
        self.session = session
        self.on_sign_out = on_sign_out
        self.last = time.monotonic()
        self.locked = False
        self.notice: QFrame | None = None
        QApplication.instance().installEventFilter(self)
        self.timer = QTimer(self)
        self.timer.timeout.connect(self._tick)
        self.timer.start(5000)

    def stop(self):
        self.timer.stop()
        QApplication.instance().removeEventFilter(self)
        self._hide_notice()

    def eventFilter(self, _obj, event):
        if not self.locked and event.type() in ACTIVITY:
            self.last = time.monotonic()
            if self.notice is not None:
                self._hide_notice()
        return False

    def _limit(self) -> int:
        # Read every time: a SuperAdmin's change in Settings applies here at once.
        return max(5, int(self.session.idle_lock_minutes or 25)) * 60

    def _tick(self):
        if self.locked or not self.session.is_authenticated:
            return
        idle = time.monotonic() - self.last
        if idle >= self._limit():
            self._lock()
        elif idle >= self._limit() - WARN_SECONDS and self.notice is None:
            self._show_notice()

    # -- "Still there?" ---------------------------------------------------
    def _show_notice(self):
        notice = QFrame(self.window)
        notice.setProperty("role", "card")
        notice.setObjectName("idleNotice")
        row = QHBoxLayout(notice)
        row.setContentsMargins(16, 12, 12, 12)
        text = QLabel("Still there? The app locks in a minute.")
        row.addWidget(text)
        here = QPushButton("I'm here")
        here.setProperty("role", "primary")
        here.clicked.connect(self._still_here)
        row.addWidget(here)
        notice.adjustSize()
        notice.move(self.window.width() - notice.width() - 28, self.window.height() - notice.height() - 40)
        notice.show()
        notice.raise_()
        self.notice = notice

    def _still_here(self):
        self.last = time.monotonic()
        self._hide_notice()

    def _hide_notice(self):
        if self.notice is not None:
            self.notice.deleteLater()
            self.notice = None

    # -- the lock ---------------------------------------------------------
    def _lock(self):
        self._hide_notice()
        self.locked = True
        dialog = LockDialog(self.api_client, self.session, minutes=self._limit() // 60, parent=self.window)
        unlocked = dialog.exec()
        self.locked = False
        self.last = time.monotonic()
        if not unlocked and dialog.sign_out:
            self.stop()
            if not dialog.by_server:
                self.on_sign_out(dialog.reason)


class LockDialog(QDialog):
    """Covers the main window; only the account's password opens it."""

    def __init__(self, api_client, session, minutes: int, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.session = session
        self.sign_out = False
        self.by_server = False
        self.reason = ""
        self.setWindowFlags(Qt.WindowType.Dialog | Qt.WindowType.FramelessWindowHint)
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground)
        self.setModal(True)
        if parent is not None:
            self.setGeometry(parent.geometry())

        card = QFrame()
        card.setProperty("role", "card")
        card.setFixedWidth(400)
        box = QVBoxLayout(card)
        box.setContentsMargins(28, 24, 28, 24)
        box.setSpacing(10)
        lock = QLabel()
        lock.setPixmap(pixmap("lock", 34, theme.ACCENT))
        lock.setAlignment(Qt.AlignmentFlag.AlignCenter)
        box.addWidget(lock)
        title = QLabel("Locked")
        title.setProperty("role", "title")
        title.setAlignment(Qt.AlignmentFlag.AlignCenter)
        box.addWidget(title)
        why = QLabel(f"Dateroem Admin Control locked itself after {minutes} minutes without use. "
                     f"Enter the password for {session.name or session.user_name} to carry on - nothing was lost.")
        why.setProperty("role", "muted")
        why.setWordWrap(True)
        why.setAlignment(Qt.AlignmentFlag.AlignCenter)
        box.addWidget(why)
        self.password = QLineEdit()
        self.password.setEchoMode(QLineEdit.EchoMode.Password)
        self.password.setPlaceholderText("Password")
        self.password.returnPressed.connect(self._unlock)
        box.addWidget(self.password)
        self.error = QLabel("")
        self.error.setProperty("role", "error")
        self.error.setWordWrap(True)
        box.addWidget(self.error)
        self.unlock_btn = QPushButton("Unlock")
        self.unlock_btn.setProperty("role", "primary")
        self.unlock_btn.clicked.connect(self._unlock)
        box.addWidget(self.unlock_btn)
        leave = QPushButton("Sign out")
        leave.clicked.connect(lambda: self._leave(""))
        box.addWidget(leave)

        outer = QVBoxLayout(self)
        outer.addStretch(1)
        row = QHBoxLayout()
        row.addStretch(1)
        row.addWidget(card)
        row.addStretch(1)
        outer.addLayout(row)
        outer.addStretch(1)
        self.password.setFocus()

    def paintEvent(self, _event):
        p = QPainter(self)
        shade = QColor(theme.BACKGROUND)
        shade.setAlpha(238)
        p.fillRect(QRectF(self.rect()), shade)
        p.end()

    def keyPressEvent(self, event):
        # Escape doesn't open it.
        if event.key() == Qt.Key.Key_Escape:
            return
        super().keyPressEvent(event)

    def reject(self):
        # Only Unlock or Sign out close the lock.
        if self.sign_out:
            super().reject()

    def _unlock(self):
        if not self.password.text():
            self.error.setText("Enter your password.")
            return
        self.unlock_btn.setEnabled(False)
        try:
            self.api_client.post("/auth/me/verify-password", json={"password": self.password.text()})
        except ApiError as exc:
            self.unlock_btn.setEnabled(True)
            self.password.clear()
            if exc.status_code == 401:
                # The sign-in ended meanwhile; main.py already goes back to
                # the sign-in screen (ApiClient's session_ended).
                self.by_server = True
                self._leave("")
            elif exc.status_code == 429:
                self._leave("Signed out after too many wrong passwords.")
            else:
                self.error.setText(readable(str(exc.detail)))
            return
        self.accept()

    def _leave(self, reason: str):
        self.sign_out = True
        self.reason = reason
        super().reject()
