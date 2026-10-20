from typing import Callable

from PySide6.QtCore import QThreadPool
from PySide6.QtWidgets import QVBoxLayout, QWidget

from app.core.api_client import ApiError
from app.core.worker import Worker
from app.ui.widgets.data_table import DataTable


class GenericListSection(QWidget):
    """A whole admin section (Products, Marketing, Trips, Deliveries,
    Shipping, ...) is just this: one DataTable wired to one fetch
    function. fetch_fn(limit) -> either a plain list of dicts, or a dict
    shaped {"rows": [...], "total": N} (the raw table browser's shape).
    Covers every section that's fundamentally "browse a list from the
    server" without writing a bespoke screen for each one."""

    def __init__(
        self,
        title: str,
        fetch_fn: Callable[[object], object],
        show_limit_selector: bool = True,
        default_limit_label: str = "50",
        parent=None,
        searchable: bool = False,
        search_placeholder: str = "Search",
    ):
        super().__init__(parent)
        self.fetch_fn = fetch_fn

        layout = QVBoxLayout(self)
        self.table = DataTable(
            title,
            show_limit_selector=show_limit_selector,
            default_limit_label=default_limit_label,
            searchable=searchable,
            search_placeholder=search_placeholder,
        )
        self.table.refresh_requested.connect(self.load)
        layout.addWidget(self.table)

        # Holds every in-flight Worker so it isn't garbage-collected before
        # its signals fire (QThreadPool.start() doesn't keep the Python
        # wrapper alive on its own) - never trimmed, but for an admin tool
        # a few dozen finished workers accumulating over a session is
        # negligible.
        self._active_workers = []

        self.load(self.table.current_limit())

    def load(self, limit):
        self.table.set_loading()

        def work():
            return self.fetch_fn(limit)

        worker = Worker(work)
        worker.signals.finished.connect(self._on_loaded)
        worker.signals.error.connect(self._on_error)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)

    def _on_loaded(self, data):
        if isinstance(data, dict) and "rows" in data:
            self.table.set_rows(data["rows"], data.get("total"))
        elif isinstance(data, list):
            self.table.set_rows(data)
        else:
            self.table.set_rows([])

    def _on_error(self, message: str):
        self.table.set_error(message)
