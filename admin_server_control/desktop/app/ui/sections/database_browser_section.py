from functools import partial

from PySide6.QtCore import QThreadPool
from PySide6.QtWidgets import QHBoxLayout, QLabel, QMenu, QPushButton, QVBoxLayout, QWidget

from app.core.worker import Worker
from app.ui.widgets.data_table import DataTable

# "categories to this databases e.g base production tables, shipping and
# sales tables, transaction tables and marketing tables ... so its easier
# to see the progress and easier access." One popup submenu per category,
# each holding only that category's tables. Any real table not listed here
# (a future migration, etc.) still shows up, under "Other" - this is a
# display grouping only, never a filter.
TABLE_CATEGORIES = [
    ("Base & production", [
        "ROLE", "USERS", "FARMER", "REMAINDER_TREES", "TRIP", "VEHICLE",
        "MACHINE", "TREES", "PRODUCTS", "CUSTOMER_PROFILES",
    ]),
    ("Shipping & sales", [
        "ORDERS", "ORDER_ITEMS", "BULK_DELIVERY", "LIGHT_DELIVERIES",
        "DELIVERY_AREAS", "BULK_TRIP_PRODUCTION_DESCRIPTION",
        "INVENTORY_MOVEMENT", "PRODUCT_PAYMENTS",
    ]),
    ("Transactions & finance", [
        "FINANCIAL_TRANSACTION", "PROFIT", "FARMER_PAYMENTS", "SALARIES",
        "TRANSPORT", "MACHINE_EXPENSES", "ACCOMODATION", "OTHER_EXPENSES",
        "PERMITS", "CAPITAL", "LOAN", "LOAN_REPAYMENTS",
    ]),
    ("Marketing", [
        "CAMPAIGN", "CAMPAIGN_AUDIENCE", "CAMPAIGN_LOGS", "CAMPAIGN_PRODUCTS",
    ]),
    ("System & communications", [
        "SMS_QUEUE", "COMMUNICATIONS", "AI_CHAT_MESSAGES", "AUDIT_LOGS",
        "EVENTS",
    ]),
]


class DatabaseBrowserSection(QWidget):
    """"one should be able to even see the databases and the data in them
    by e.g specifying all, only 50 etc and all the sections". A raw view
    over every table in the database - not reshaped, not renamed - for
    when the curated sections (Products, Trips, ...) aren't specific
    enough, or a table has no curated section at all (ORDERS, TREES,
    VEHICLE, AUDIT_LOGS, ...).

    "its taking too much space ... i want a tab e.g base operations but
    this is a drop down list ... this allows small interference with the
    table viewing area and it allows the selection to be a pop up not an
    entire space" - one single button, one row. Clicking it pops up the
    category names; clicking a category expands its own tables as a
    submenu; clicking a table closes the whole popup and loads it. No
    permanent tab bar, no permanent list - just that one row until
    it's clicked."""

    def __init__(self, api_client, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.current_table: str | None = None
        # See GenericListSection - keeps in-flight Workers from being
        # garbage-collected before their signals fire.
        self._active_workers = []

        layout = QVBoxLayout(self)

        picker_row = QHBoxLayout()
        picker_row.addWidget(QLabel("Table:"))
        self.picker_button = QPushButton("Loading tables...")
        self.picker_button.setEnabled(False)
        self.picker_menu = QMenu(self.picker_button)
        self.picker_button.setMenu(self.picker_menu)
        picker_row.addWidget(self.picker_button)
        picker_row.addStretch()
        layout.addLayout(picker_row)

        # The row view below - the only thing that grows, since the
        # picker above is a single fixed-height row regardless of how
        # many tables or categories exist.
        self.data_table = DataTable("Select a table above", show_limit_selector=True, searchable=True, search_placeholder="Search these rows")
        self.data_table.refresh_requested.connect(self._load_rows)
        layout.addWidget(self.data_table)

        self._load_table_names()

    def _load_table_names(self):
        def work():
            return self.api_client.get("/admin/tables")

        worker = Worker(work)
        worker.signals.finished.connect(self._on_tables_loaded)
        worker.signals.error.connect(self.data_table.set_error)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)

    def _on_tables_loaded(self, tables: list):
        available = set(tables or [])
        known = {name for _, names in TABLE_CATEGORIES for name in names}
        remaining = sorted(available - known)
        groups = list(TABLE_CATEGORIES)
        if remaining:
            groups = groups + [("Other", remaining)]

        self.picker_menu.clear()
        first_choice: tuple[str, str] | None = None
        for category, names in groups:
            present = [name for name in names if name in available]
            if not present:
                continue
            submenu = self.picker_menu.addMenu(category)
            for name in present:
                action = submenu.addAction(name)
                action.triggered.connect(partial(self._on_table_chosen, category, name))
            if first_choice is None:
                first_choice = (category, present[0])

        self.picker_button.setEnabled(bool(first_choice))
        if first_choice:
            self._on_table_chosen(*first_choice)
        else:
            self.picker_button.setText("No tables")

    def _on_table_chosen(self, category: str, table_name: str):
        if not table_name:
            return
        self.current_table = table_name
        self.picker_button.setText(f"{category}  ▸  {table_name}")
        self._load_rows(self.data_table.current_limit())

    def _load_rows(self, limit):
        if not self.current_table:
            return
        self.data_table.set_loading()
        table_name = self.current_table

        def work():
            return self.api_client.get(f"/admin/tables/{table_name}", params={"limit": limit} if limit else None)

        def on_done(data: dict):
            # Ignore a slow response for a table the user has since
            # navigated away from - avoids the classic "stale response
            # overwrites what's now on screen" race when switching quickly.
            if table_name != self.current_table:
                return
            self.data_table.set_rows((data or {}).get("rows") or [], (data or {}).get("total"))

        worker = Worker(work)
        worker.signals.finished.connect(on_done)
        worker.signals.error.connect(self.data_table.set_error)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)
