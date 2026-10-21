"""Standalone entry point for the Start Menu shortcut / double-click launch.

Doesn't rely on the working directory being set correctly (Windows
shortcuts can't reliably use a UNC "Start in" path, which is what this
project lives under) - it fixes up sys.path from its own file location
instead, so `python launch.pyw` (or pythonw.exe, for no console window)
works from anywhere.

"took too long to open ... one thinks one did not click the app" - the
heavy imports (every section, requests, the speech bits) used to run
BEFORE any window existed, so a click showed nothing for seconds. Now a
small "Starting..." window is up within a moment of the click, and the
real app loads behind it.

"the desktop does not behave like other applications ... starting ... it
does not go away from the screen when someone clicks it ... allow one to
navigate without getting blocked" - that window used to stay on top of
every other window, and while the app loaded (about ten seconds from this
project's WSL folder) it could not even redraw, so Windows treated it as
frozen. Now it is an ordinary window other windows can cover, and the app
loads on a helper thread while this one keeps the window responsive.
"""
import sys
import threading
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

# The Qt modules the app uses are loaded here, on the main thread; only
# the app's own modules load on the helper thread.
from PySide6 import QtGui, QtNetwork, QtSvg  # noqa: E402,F401
from PySide6.QtCore import QEventLoop, QRectF, Qt, QTimer  # noqa: E402
from PySide6.QtGui import QColor, QFont, QLinearGradient, QPainter, QPainterPath, QPen, QPixmap  # noqa: E402
from PySide6.QtWidgets import QApplication, QWidget  # noqa: E402

from app.core.single_instance import SingleInstanceGuard  # noqa: E402
from app.core.windows_app_id import set_windows_app_id  # noqa: E402
from app.ui import theme  # noqa: E402


class _BootCard(QWidget):
    """The "Starting" window, styled as the door to the forest intro that
    follows ("can it have the forest background image", 21 October 2026): one
    small ready-made picture (the misty forest in the app's greens with the
    emblem, the wordmark and ADMIN CONTROL - app/ui/assets/entrance/
    boot_card.jpg, 30 KB, made by make_boot_card.py) so it is up as quickly as
    before, "Starting..." and a slim wood bar that keeps moving while the app
    loads behind it."""

    W, H = 440, 250

    def __init__(self):
        super().__init__(None, Qt.WindowType.FramelessWindowHint)
        self.setWindowTitle("Dateroem Admin Control")
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground)
        self.setFixedSize(self.W, self.H)
        self.card = QPixmap(str(Path(theme.ENTRANCE_FOREST).with_name("boot_card.jpg")))
        if not self.card.isNull():
            self.card.setDevicePixelRatio(self.card.width() / self.W)
        self.phase = 0.0
        self.timer = QTimer(self)
        self.timer.timeout.connect(self._tick)
        self.timer.start(33)
        screen = QApplication.primaryScreen().availableGeometry()
        self.move(screen.center().x() - self.W // 2, screen.center().y() - self.H // 2)

    def _tick(self):
        self.phase = (self.phase + 0.014) % 1.0
        self.update(0, 196, self.W, 16)

    def closeEvent(self, event):
        # main() closes this once the intro is up; the bar stops with it.
        self.timer.stop()
        super().closeEvent(event)

    def paintEvent(self, _event):
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        p.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform)
        outline = QPainterPath()
        outline.addRoundedRect(QRectF(self.rect()).adjusted(0.5, 0.5, -0.5, -0.5), 14, 14)
        p.setClipPath(outline)
        if self.card.isNull():
            p.fillRect(self.rect(), QColor(theme.BACKGROUND))
        else:
            p.drawPixmap(self.rect(), self.card)
        font = QFont("Segoe UI")
        font.setPixelSize(14)
        p.setFont(font)
        p.setPen(QColor(245, 245, 241, 215))
        p.drawText(QRectF(0, 166, self.W, 24), Qt.AlignmentFlag.AlignCenter, "Starting...")
        # A slim track with a short stretch of wood gliding along it.
        track = QRectF(self.W / 2 - 90, 203, 180, 3)
        p.setPen(Qt.PenStyle.NoPen)
        p.setBrush(QColor(255, 255, 255, 34))
        p.drawRoundedRect(track, 1.5, 1.5)
        span = 64
        x = track.left() - span + (track.width() + span) * self.phase
        wood = QLinearGradient(x, 0, x + span, 0)
        wood.setColorAt(0.0, QColor(244, 181, 102, 0))
        wood.setColorAt(0.5, QColor(244, 181, 102, 255))
        wood.setColorAt(1.0, QColor(232, 137, 47, 0))
        p.save()
        p.setClipRect(track)
        p.setBrush(wood)
        p.drawRoundedRect(QRectF(x, track.top(), span, track.height()), 1.5, 1.5)
        p.restore()
        p.setClipping(False)
        p.setPen(QPen(QColor(235, 154, 75, 110), 1))
        p.setBrush(Qt.BrushStyle.NoBrush)
        p.drawPath(outline)
        p.end()


def _boot_window() -> QWidget:
    return _BootCard()


def _load_app(qt_app: QApplication):
    """Imports app.main on a helper thread and keeps this thread's windows
    responsive until it is done; returns main()."""
    loaded = {}

    def load():
        try:
            from app.main import main

            loaded["main"] = main
        except BaseException as exc:  # noqa: BLE001 - re-raised below, on this thread
            loaded["error"] = exc

    thread = threading.Thread(target=load, name="load-app", daemon=True)
    thread.start()
    while thread.is_alive():
        qt_app.processEvents(QEventLoop.ProcessEventsFlag.AllEvents, 30)
        thread.join(0.02)
    if "error" in loaded:
        raise loaded["error"]
    return loaded["main"]


if __name__ == "__main__":
    set_windows_app_id()
    qt_app = QApplication(sys.argv)
    guard = SingleInstanceGuard()
    if guard.try_acquire():
        boot = _boot_window()
        boot.show()
        # Clicking the shortcut again while loading brings this window back
        # to the front instead of doing nothing; main() takes over the
        # signal once the real windows exist.
        guard.activate_requested.connect(boot.raise_)
        guard.activate_requested.connect(boot.activateWindow)
        main = _load_app(qt_app)
        guard.activate_requested.disconnect(boot.raise_)
        guard.activate_requested.disconnect(boot.activateWindow)

        main(app=qt_app, guard=guard, boot=boot)
