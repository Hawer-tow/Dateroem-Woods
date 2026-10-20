"""Keeps this app to one running copy - like WhatsApp/Netflix on desktop,
clicking the taskbar/Start Menu icon while it's already open should just
bring the existing window forward, not launch a second process on top of
it. A QLocalServer under a fixed name acts as the "lock": the first
instance to successfully listen on that name is the primary one; every
later launch fails to bind, taps the primary instance over the same named
pipe to ask it to raise itself, and exits immediately."""
from PySide6.QtCore import QObject, Signal
from PySide6.QtNetwork import QLocalServer, QLocalSocket

SERVER_NAME = "DateroemAdminControl_SingleInstance_v1"


class SingleInstanceGuard(QObject):
    activate_requested = Signal()

    def __init__(self):
        super().__init__()
        self._server = None

    def try_acquire(self) -> bool:
        """True if this is the primary instance. False means another
        instance is already running and has been notified to raise
        itself - the caller should exit right away without creating any
        windows."""
        probe = QLocalSocket()
        probe.connectToServer(SERVER_NAME)
        if probe.waitForConnected(300):
            probe.write(b"show")
            probe.waitForBytesWritten(300)
            probe.disconnectFromServer()
            return False

        # Nothing answered - claim the name ourselves. removeServer() first
        # clears out a stale named-pipe registration that can be left
        # behind if the previous primary instance was killed rather than
        # closed normally.
        QLocalServer.removeServer(SERVER_NAME)
        self._server = QLocalServer(self)
        self._server.newConnection.connect(self._on_new_connection)
        self._server.listen(SERVER_NAME)
        return True

    def _on_new_connection(self):
        socket = self._server.nextPendingConnection()
        if socket is None:
            return
        socket.readyRead.connect(lambda: self._on_ready_read(socket))

    def _on_ready_read(self, socket):
        socket.readAll()
        self.activate_requested.emit()
        socket.disconnectFromServer()
