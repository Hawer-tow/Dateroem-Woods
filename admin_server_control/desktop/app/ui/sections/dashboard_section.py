from PySide6.QtCore import QThreadPool
from PySide6.QtWidgets import QFrame, QGridLayout, QLabel, QVBoxLayout, QWidget

from app.core.worker import Worker
from app.ui import theme


class _StatCard(QFrame):
    def __init__(self, title: str, parent=None):
        super().__init__(parent)
        self.setStyleSheet(f"QFrame {{ background-color: {theme.SURFACE}; border-radius: 8px; }}")
        layout = QVBoxLayout(self)
        self.title_label = QLabel(title)
        self.title_label.setProperty("role", "muted")
        self.value_label = QLabel("...")
        self.value_label.setProperty("role", "title")
        layout.addWidget(self.title_label)
        layout.addWidget(self.value_label)

    def set_value(self, value):
        self.value_label.setText(str(value))


class DashboardSection(QWidget):
    """Landing screen - a quick read on the whole system before drilling
    into any one section, same spirit as the mobile app's own dashboard."""

    def __init__(self, session, api_client, parent=None):
        super().__init__(parent)
        self.session = session
        self.api_client = api_client
        # See GenericListSection - keeps in-flight Workers from being
        # garbage-collected before their signals fire. Five concurrent
        # refresh() calls means five live workers at once here.
        self._active_workers = []

        layout = QVBoxLayout(self)
        heading = QLabel(f"Welcome, {session.name} ({session.role_name})")
        heading.setProperty("role", "title")
        layout.addWidget(heading)

        grid = QGridLayout()
        self.tables_card = _StatCard("Database tables")
        self.products_card = _StatCard("Products")
        self.campaigns_card = _StatCard("Campaigns")
        self.trips_card = _StatCard("Active trips")
        self.users_card = _StatCard("Users")
        for i, card in enumerate(
            [self.tables_card, self.products_card, self.campaigns_card, self.trips_card, self.users_card]
        ):
            grid.addWidget(card, i // 3, i % 3)
        layout.addLayout(grid)
        layout.addStretch()

        self.refresh()

    def refresh(self):
        self._load(lambda: self.api_client.get("/admin/tables"), lambda d: self.tables_card.set_value(len(d or [])))
        self._load(lambda: self.api_client.get("/products"), lambda d: self.products_card.set_value(len(d or [])))
        self._load(lambda: self.api_client.get("/marketing/campaigns"), lambda d: self.campaigns_card.set_value(len(d or [])))
        self._load(lambda: self.api_client.get("/trips"), lambda d: self.trips_card.set_value(len(d or [])))
        self._load(lambda: self.api_client.get("/users"), lambda d: self.users_card.set_value(len(d or [])))

    def _load(self, fn, on_done):
        worker = Worker(fn)
        worker.signals.finished.connect(on_done)
        worker.signals.error.connect(lambda _msg: None)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)
