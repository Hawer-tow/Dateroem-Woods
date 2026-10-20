from PySide6.QtWidgets import QMainWindow

from app.core.server_manager import ServerManager
from app.ui.widgets.server_control_panel import ServerControlPanel


class ServerControlWindow(QMainWindow):
    """The pre-login route into server control - "im puzzled on how this
    will work since logging into the desktop i think requires it to have
    the backend server on". Reachable straight from the sign-in screen
    (LoginWindow owns a button that creates/shows this), independent of
    any login or admin-API call: starting/stopping is pure local process
    control against the same ServerManager the post-login Servers
    section also uses, so state (what's running, each log) is identical
    no matter which one you open it from. Nobody is signed in here, so it
    can only start servers - stopping needs the SuperAdmin, signed in."""

    def __init__(self, manager: ServerManager, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Server Control")
        self.resize(900, 620)
        self.setCentralWidget(ServerControlPanel(manager))
