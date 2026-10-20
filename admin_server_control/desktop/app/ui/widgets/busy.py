"""One shared "something is happening" behaviour for every action button -
"all save a button that behaves the same ... a loading function to allow
one to know saving is occurring". While busy the button is disabled and
its label animates ("Saving", "Saving.", "Saving..", ...) so a slow
request never looks like a click that did nothing. Refresh buttons spin
their arrows instead (set_spinning)."""
from functools import lru_cache

from PySide6.QtCore import Qt, QTimer
from PySide6.QtGui import QIcon, QPainter, QPixmap
from PySide6.QtWidgets import QPushButton

from app.ui import theme
from app.ui.icons import icon, pixmap

SPIN_STEPS = 24  # 15 degrees a step
SPIN_STEP_MS = 30  # one full turn about every 0.7 s


def set_busy(button: QPushButton, busy: bool, text: str = "Saving"):
    if busy:
        if getattr(button, "_busy_timer", None) is not None:
            return
        button._busy_original_text = button.text()
        button.setEnabled(False)
        step = [0]

        def tick():
            step[0] = (step[0] + 1) % 4
            button.setText(text + "." * step[0])

        timer = QTimer(button)
        timer.timeout.connect(tick)
        timer.start(350)
        button._busy_timer = timer
        button.setText(text)
        return

    timer = getattr(button, "_busy_timer", None)
    if timer is None:
        return
    timer.stop()
    timer.deleteLater()
    button._busy_timer = None
    button.setText(getattr(button, "_busy_original_text", button.text()))
    button.setEnabled(True)


@lru_cache(maxsize=16)
def _spin_frames(name: str, size: int, color: str) -> tuple:
    """The icon turned a little further in each frame. Kept identical when
    the button is disabled, so the arrows stay bright while they turn."""
    base = pixmap(name, size, color)
    flat = QPixmap(base)
    flat.setDevicePixelRatio(1.0)
    width, height = flat.width(), flat.height()
    frames = []
    for step in range(SPIN_STEPS):
        frame = QPixmap(width, height)
        frame.fill(Qt.GlobalColor.transparent)
        painter = QPainter(frame)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        painter.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform)
        painter.translate(width / 2, height / 2)
        painter.rotate(step * 360 / SPIN_STEPS)
        painter.translate(-width / 2, -height / 2)
        painter.drawPixmap(0, 0, flat)
        painter.end()
        frame.setDevicePixelRatio(base.devicePixelRatio())
        turned = QIcon()
        turned.addPixmap(frame, QIcon.Mode.Normal)
        turned.addPixmap(frame, QIcon.Mode.Disabled)
        frames.append(turned)
    return tuple(frames)


def set_spinning(button: QPushButton, spinning: bool, icon_name: str = "refresh", color: str | None = None):
    """"why not rotate those arrows ... more logical to expect than the
    refreshing having the '...' things" - while a refresh runs, the button's
    own curved arrows turn round (its label stays as it is) and it can't be
    clicked again; when it's done the arrows stop where they started."""
    if spinning:
        if getattr(button, "_spin_timer", None) is not None:
            return
        size = button.iconSize().width() or 16
        frames = _spin_frames(icon_name, size, color or theme.TEXT)
        button.setEnabled(False)
        step = [0]

        def tick():
            step[0] = (step[0] + 1) % len(frames)
            button.setIcon(frames[step[0]])

        timer = QTimer(button)
        timer.timeout.connect(tick)
        timer.start(SPIN_STEP_MS)
        button._spin_timer = timer
        button.setIcon(frames[0])
        return

    timer = getattr(button, "_spin_timer", None)
    if timer is None:
        return
    timer.stop()
    timer.deleteLater()
    button._spin_timer = None
    button.setIcon(icon(icon_name, color))
    button.setEnabled(True)
