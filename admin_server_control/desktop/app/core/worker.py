from PySide6.QtCore import QObject, QRunnable, Signal, Slot


class WorkerSignals(QObject):
    finished = Signal(object)
    error = Signal(str)


class Worker(QRunnable):
    """Runs one blocking call (an ApiClient request) off the UI thread, so
    a slow/unreachable admin server never freezes the window. Use via
    QThreadPool.globalInstance().start(Worker(fn, *args))."""

    def __init__(self, fn, *args, **kwargs):
        super().__init__()
        self.fn = fn
        self.args = args
        self.kwargs = kwargs
        self.signals = WorkerSignals()

    @Slot()
    def run(self):
        try:
            result = self.fn(*self.args, **self.kwargs)
        except Exception as exc:  # noqa: BLE001 - surfaced to the UI, not swallowed
            self._emit(self.signals.error, str(exc))
        else:
            self._emit(self.signals.finished, result)

    @staticmethod
    def _emit(signal, value):
        try:
            signal.emit(value)
        except RuntimeError:
            # The window that asked was closed while this ran (a photo
            # still loading in a form that was cancelled) - nobody is
            # listening any more, so the answer is simply dropped.
            pass
