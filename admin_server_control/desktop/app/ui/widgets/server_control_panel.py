import math
import time

from PySide6.QtCore import QTimer
from PySide6.QtGui import QTextCursor
from PySide6.QtWidgets import (
    QHBoxLayout,
    QLabel,
    QMessageBox,
    QPlainTextEdit,
    QProgressBar,
    QPushButton,
    QTabWidget,
    QVBoxLayout,
    QWidget,
)

from app.core.server_manager import ServerDef, ServerManager
from app.ui import theme
from app.ui.icons import icon, pixmap
from app.ui.widgets.busy import set_spinning

RUNNING_COLOR = "#4CAF50"
# "if i click start or start all or stop all i should see some reaction" -
# every server's state has its own colour, used for its status dot and the
# icon on its tab, so the whole set reads at a glance.
STATE_COLORS = {
    "running": RUNNING_COLOR,
    "stopped": theme.TEXT_MUTED,
    "failed": theme.DANGER,
    "starting": theme.WOOD,
    "stopping": theme.WOOD,
}
BUSY_STATES = ("starting", "stopping")
STOP_LOCKED = "Anyone can start the servers; only the SuperAdmin can stop them. Sign in as the SuperAdmin to stop a server."


def _confirm_stop(parent, question: str) -> bool:
    # Stopping takes the website, the phones and every other admin off line
    # until someone starts the servers again.
    answer = QMessageBox.question(
        parent, "Stop servers?",
        f"{question}\n\nThe website, the phone app and the other admins lose access until it is started again.",
    )
    return answer == QMessageBox.StandardButton.Yes


class _ServerTab(QWidget):
    """One server's own tab: status dot, Start/Stop, and its live log.
    What it shows comes from the shared ServerManager's state for the
    server - the real port check, plus "starting"/"stopping" while a click
    is still taking effect - not from whether this ServerManager instance
    happens to hold the process handle, so a server started from the .bat
    script (or a previous run of this app) still shows correctly as running
    and is still stoppable here."""

    def __init__(self, server_def: ServerDef, manager: ServerManager, can_stop, parent=None):
        super().__init__(parent)
        self.server_def = server_def
        self.manager = manager
        self.can_stop = can_stop
        self._phase = 0.0

        layout = QVBoxLayout(self)

        top_row = QHBoxLayout()
        self.status_dot = QLabel("\u25CF")
        top_row.addWidget(self.status_dot)
        self.status_label = QLabel("Checking...")
        top_row.addWidget(self.status_label)
        address = QLabel(f"  127.0.0.1:{server_def.port}")
        address.setProperty("role", "muted")
        top_row.addWidget(address)
        top_row.addStretch(1)

        self.start_btn = QPushButton("Start")
        self.start_btn.setProperty("role", "primary")
        self.start_btn.setIcon(icon("play", theme.WOOD_ON))
        self.start_btn.clicked.connect(self._on_start_clicked)
        top_row.addWidget(self.start_btn)

        self.stop_btn = QPushButton("Stop")
        self.stop_btn.setProperty("role", "danger")
        self.stop_btn.setIcon(icon("stop", theme.DANGER))
        self.stop_btn.clicked.connect(self._on_stop_clicked)
        top_row.addWidget(self.stop_btn)

        clear_btn = QPushButton("Clear log")
        clear_btn.clicked.connect(lambda: self.log_view.clear())
        top_row.addWidget(clear_btn)

        layout.addLayout(top_row)

        self.log_view = QPlainTextEdit()
        self.log_view.setReadOnly(True)
        self.log_view.setMaximumBlockCount(5000)
        self.log_view.setStyleSheet(
            f"background-color: {theme.SURFACE_ALT}; color: {theme.TEXT}; "
            f"font-family: Consolas, 'Courier New', monospace; font-size: 12px; "
            f"border: 1px solid {theme.BORDER}; border-radius: 6px; padding: 8px;"
        )
        self.log_view.setPlainText(manager.log_text(server_def.key))
        self._scroll_to_bottom()
        layout.addWidget(self.log_view, 1)

        # While starting/stopping: the dot breathes and the seconds count up.
        self._pulse = QTimer(self)
        self._pulse.timeout.connect(self._on_pulse)
        self._clock = QTimer(self)
        self._clock.timeout.connect(self._refresh)

        manager.log_appended.connect(self._on_log_appended)
        manager.status_checked.connect(self._on_status_checked)
        manager.state_changed.connect(self._on_state_changed)

        self._refresh()

    def _on_start_clicked(self):
        self.manager.start(self.server_def.key)
        self._refresh()

    def _on_stop_clicked(self):
        if not self.can_stop() or not _confirm_stop(self, f"Stop {self.server_def.label}?"):
            return
        self.manager.stop(self.server_def.key)
        self._refresh()

    def _on_log_appended(self, key: str, text: str):
        if key != self.server_def.key:
            return
        self.log_view.moveCursor(QTextCursor.MoveOperation.End)
        self.log_view.insertPlainText(text)
        self._scroll_to_bottom()

    def _on_status_checked(self, key: str, _is_open: bool):
        if key == self.server_def.key:
            self._refresh()

    def _on_state_changed(self, key: str, _state: str):
        if key == self.server_def.key:
            self._refresh()

    def _refresh(self):
        state = self.manager.state(self.server_def.key)
        busy = state in BUSY_STATES
        color = STATE_COLORS[state]

        if busy:
            seconds = self.manager.transition_seconds(self.server_def.key)
            verb = "Starting" if state == "starting" else "Stopping"
            self.status_label.setText(f"{verb}...  {seconds} s")
            if not self._pulse.isActive():
                self._phase = 0.0
                self._pulse.start(60)
                self._clock.start(1000)
        else:
            self._pulse.stop()
            self._clock.stop()
            self.status_dot.setStyleSheet(f"color: {color}; font-size: 16px;")
            self.status_label.setText({
                "running": "Running",
                "stopped": "Stopped",
                "failed": "Didn't start - see the log below",
            }[state])
        self.status_label.setStyleSheet(f"color: {color if state != 'stopped' else theme.TEXT_MUTED};")

        # Start: spins while starting.
        if state == "starting":
            self.start_btn.setText("Starting")
            set_spinning(self.start_btn, True, "loader", theme.WOOD_ON)
        else:
            set_spinning(self.start_btn, False, "loader", theme.WOOD_ON)
            self.start_btn.setIcon(icon("play", theme.WOOD_ON))
            self.start_btn.setText("Try again" if state == "failed" else "Start")
            self.start_btn.setEnabled(state in ("stopped", "failed"))

        # Stop: spins while stopping; also stops a server that's still starting.
        if state == "stopping":
            self.stop_btn.setText("Stopping")
            set_spinning(self.stop_btn, True, "loader", theme.DANGER)
        else:
            set_spinning(self.stop_btn, False, "loader", theme.DANGER)
            self.stop_btn.setIcon(icon("stop", theme.DANGER))
            self.stop_btn.setText("Stop")
            self.stop_btn.setEnabled(state in ("running", "starting") and self.can_stop())
        self.stop_btn.setToolTip("" if self.can_stop() else STOP_LOCKED)

    def _on_pulse(self):
        self._phase += 0.35
        alpha = 0.3 + 0.7 * (0.5 + 0.5 * math.sin(self._phase))
        self.status_dot.setStyleSheet(f"color: rgba(235, 154, 75, {alpha:.2f}); font-size: 16px;")

    def _scroll_to_bottom(self):
        scrollbar = self.log_view.verticalScrollBar()
        scrollbar.setValue(scrollbar.maximum())


class ServerControlPanel(QWidget):
    """"instead of the terminals opening outside the app you see them
    inside the app in sections in there respective names like selection
    tabs e.g mobile server backend server and the logs for each appearing
    section in it we can have the functionality start the server stop
    the server". A thin view over the shared ServerManager - safe to
    create more than one instance (the pre-login window and the
    post-login Servers section each make their own), since all real
    state (process handles, log buffers, port status, starting/stopping)
    lives in the one shared manager, not in this widget.

    "only superadmin can start and stop but normal user can only start" -
    can_stop says whether whoever is using the app may stop servers (only
    a signed-in SuperAdmin); everyone, signed in or not, can start them,
    so an honest admin can always bring the system back."""

    def __init__(self, manager: ServerManager, can_stop=None, parent=None):
        super().__init__(parent)
        self.manager = manager
        self.can_stop = can_stop or (lambda: False)
        self._batch: tuple[str, float] | None = None  # ("start"|"stop", since) after Start All / Stop All
        self._batch_launched = False

        layout = QVBoxLayout(self)
        layout.setContentsMargins(16, 16, 16, 16)

        header = QHBoxLayout()
        title_icon = QLabel()
        title_icon.setPixmap(pixmap("server", 22, theme.WOOD))
        header.addWidget(title_icon)
        title = QLabel("Servers")
        title.setProperty("role", "title")
        header.addWidget(title)
        self.summary_label = QLabel("")
        self.summary_label.setProperty("role", "muted")
        header.addWidget(self.summary_label)
        header.addStretch(1)

        self.start_all_btn = QPushButton("Start All")
        self.start_all_btn.setProperty("role", "primary")
        self.start_all_btn.setIcon(icon("play", theme.WOOD_ON))
        self.start_all_btn.clicked.connect(self._on_start_all)
        header.addWidget(self.start_all_btn)

        self.stop_all_btn = QPushButton("Stop All")
        self.stop_all_btn.setProperty("role", "danger")
        self.stop_all_btn.setIcon(icon("stop", theme.DANGER))
        self.stop_all_btn.clicked.connect(self._on_stop_all)
        self.stop_all_btn.setEnabled(self.can_stop())
        self.stop_all_btn.setToolTip("" if self.can_stop() else STOP_LOCKED)
        header.addWidget(self.stop_all_btn)

        layout.addLayout(header)

        if not self.can_stop():
            locked = QLabel(STOP_LOCKED)
            locked.setProperty("role", "muted")
            locked.setWordWrap(True)
            layout.addWidget(locked)

        # Start All / Stop All progress: a bar filling as servers come up
        # (or go down) and a line saying where it's got to.
        self.progress_row = QWidget()
        progress_layout = QHBoxLayout(self.progress_row)
        progress_layout.setContentsMargins(0, 2, 0, 2)
        self.progress = QProgressBar()
        self.progress.setTextVisible(False)
        self.progress.setFixedHeight(6)
        self.progress.setMaximumWidth(260)
        self.progress.setStyleSheet(
            f"QProgressBar {{ background: {theme.SURFACE_ALT}; border: none; border-radius: 3px; }}"
            f"QProgressBar::chunk {{ background: {theme.WOOD_SWEEP}; border-radius: 3px; }}"
        )
        progress_layout.addWidget(self.progress)
        self.progress_label = QLabel("")
        progress_layout.addWidget(self.progress_label, 1)
        self.progress_row.hide()
        layout.addWidget(self.progress_row)

        note = QLabel(
            "Starts and stops the same servers as C:\\Scripts\\Launch-Dateroem.bat, "
            "just inside this window instead of separate terminal windows. You can "
            "still use the script directly any time you don't want to open the app."
        )
        note.setProperty("role", "muted")
        note.setWordWrap(True)
        layout.addWidget(note)

        self.tabs = QTabWidget()
        for server_def in manager.defs():
            self.tabs.addTab(
                _ServerTab(server_def, manager, self.can_stop), icon(server_def.icon, theme.TEXT_MUTED), server_def.label
            )
        layout.addWidget(self.tabs, 1)

        self._batch_clock = QTimer(self)
        self._batch_clock.timeout.connect(self._refresh_overview)
        self._hide_progress = QTimer(self)
        self._hide_progress.setSingleShot(True)
        self._hide_progress.timeout.connect(self.progress_row.hide)

        manager.status_checked.connect(lambda _key, _open: self._refresh_overview())
        manager.state_changed.connect(lambda _key, _state: self._refresh_overview())
        self._refresh_overview()

    def _on_start_all(self):
        self._begin_batch("start")
        # Let the button and bar show first, then do the (quick) launching.
        QTimer.singleShot(30, lambda: self._launch_batch(self.manager.start_all))

    def _on_stop_all(self):
        if not self.can_stop() or not _confirm_stop(self, "Stop all servers?"):
            return
        self._begin_batch("stop")
        QTimer.singleShot(30, lambda: self._launch_batch(self.manager.stop_all))

    def _launch_batch(self, action):
        action()
        self._batch_launched = True
        self._refresh_overview()

    def _begin_batch(self, kind: str):
        self._batch = (kind, time.monotonic())
        self._batch_launched = False
        self._hide_progress.stop()
        button = self.start_all_btn if kind == "start" else self.stop_all_btn
        button.setText("Starting all" if kind == "start" else "Stopping all")
        set_spinning(button, True, "loader", theme.WOOD_ON if kind == "start" else theme.DANGER)
        (self.stop_all_btn if kind == "start" else self.start_all_btn).setEnabled(False)
        self.progress.setRange(0, len(self.manager.defs()))
        self.progress_label.setStyleSheet(f"color: {theme.TEXT};")
        self.progress_label.setText("Starting servers..." if kind == "start" else "Stopping servers...")
        self.progress_row.show()
        self._batch_clock.start(1000)

    def _refresh_overview(self):
        defs = self.manager.defs()
        states = {d.key: self.manager.state(d.key) for d in defs}
        # Counted by whether each one is really answering - one still
        # shutting down is still up.
        running = sum(1 for d in defs if self.manager.last_known_status(d.key))
        total = len(defs)
        self.summary_label.setText(f"  {running} of {total} running")
        for i, server_def in enumerate(defs):
            self.tabs.setTabIcon(i, icon(server_def.icon, STATE_COLORS[states[server_def.key]]))

        # Nothing to judge until the servers have actually been told.
        if not self._batch or not self._batch_launched:
            return
        kind, since = self._batch
        seconds = int(time.monotonic() - since)
        busy = [d for d in defs if states[d.key] in BUSY_STATES]
        if kind == "start":
            self.progress.setValue(running)
            if busy:
                self.progress_label.setText(f"Starting servers...  {running} of {total} running  \u00b7  {seconds} s")
                return
            failed = [d.label for d in defs if states[d.key] == "failed"]
            if running == total:
                message, color = f"All {total} servers are running", RUNNING_COLOR
            else:
                message = f"{running} of {total} running"
                if failed:
                    message += f" - {', '.join(failed)} didn't start (open its tab for the log)"
                color = theme.DANGER if failed else theme.TEXT
        else:
            up = running
            self.progress.setValue(total - up)
            if busy:
                self.progress_label.setText(f"Stopping servers...  {up} still up  \u00b7  {seconds} s")
                return
            if running == 0:
                message, color = "All servers stopped", theme.TEXT
            else:
                still = [d.label for d in defs if states[d.key] == "running"]
                message, color = f"{', '.join(still)} still running", theme.DANGER
        self._end_batch(kind, message, color)

    def _end_batch(self, kind: str, message: str, color: str):
        self._batch = None
        self._batch_clock.stop()
        for button, text, rest_icon, tint in (
            (self.start_all_btn, "Start All", "play", theme.WOOD_ON),
            (self.stop_all_btn, "Stop All", "stop", theme.DANGER),
        ):
            set_spinning(button, False, "loader", tint)
            button.setIcon(icon(rest_icon, tint))
            button.setText(text)
            button.setEnabled(button is self.start_all_btn or self.can_stop())
        self.progress_label.setStyleSheet(f"color: {color}; font-weight: 600;")
        self.progress_label.setText(message)
        self._hide_progress.start(8000)
