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
from PySide6.QtCore import QEventLoop, Qt  # noqa: E402
from PySide6.QtWidgets import QApplication, QLabel  # noqa: E402

from app.core.single_instance import SingleInstanceGuard  # noqa: E402
from app.core.windows_app_id import set_windows_app_id  # noqa: E402
from app.ui import theme  # noqa: E402


def _boot_window() -> QLabel:
    label = QLabel("Dateroem Admin Control\n\nStarting...")
    label.setWindowTitle("Dateroem Admin Control")
    label.setAlignment(Qt.AlignmentFlag.AlignCenter)
    label.setWindowFlags(Qt.WindowType.FramelessWindowHint)
    label.setStyleSheet(
        f"background:{theme.BACKGROUND}; color:{theme.TEXT}; font-size:18px; "
        f"border:2px solid {theme.ACCENT}; padding:32px;"
    )
    label.setFixedSize(360, 180)
    screen = QApplication.primaryScreen().availableGeometry()
    label.move(screen.center().x() - 180, screen.center().y() - 90)
    return label


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
