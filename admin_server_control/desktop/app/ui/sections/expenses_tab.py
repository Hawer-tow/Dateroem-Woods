"""A trip's Expenses: one tab per category, right inside the trip.

"in the trip add a tab expenses in it add all the expenses as tabs so one can
go to a certain expense create the expense and prompt if it's there not
assigned just click on it and assign on it" - expenses belong to a trip, so
this is where they're worked with: each category lists its entries (unpaid
ones highlighted, and counted in the tab title as the prompt), a new entry can
be created, and an unpaid one is either PAID straight from the company wallet
(Send Money, receipt fills itself in) or ASSIGNED to a transaction that already
came in.
"""
from PySide6.QtCore import QThreadPool
from PySide6.QtGui import QColor
from PySide6.QtWidgets import (
    QComboBox,
    QDialog,
    QHBoxLayout,
    QLabel,
    QMessageBox,
    QPushButton,
    QTabWidget,
    QVBoxLayout,
    QWidget,
)

from app.core.worker import Worker
from app.ui import theme
from app.ui.widgets.busy import set_busy
from app.ui.widgets.data_table import DataTable
from app.ui.widgets.payout_dialog import PayoutDialog
from app.ui.widgets.record_form_dialog import FieldSpec, RecordFormDialog

UNASSIGNED_TINT = QColor(theme.WOOD)
UNASSIGNED_TINT.setAlpha(70)

PAYMENT_MODES = [("Soft (M-Pesa)", "SOFT"), ("Hard (cash)", "HARD")]

# (URL key, tab label, ExpenseTarget name, id key in the row JSON)
CATEGORIES = [
    ("transport", "Transport", "TRANSPORT", "transportId"),
    ("communications", "Communications", "COMMUNICATIONS", "communicationsId"),
    ("machine-expenses", "Machinery", "MACHINE_EXPENSES", "machineExpensesId"),
    ("accomodation", "Accommodation", "ACCOMODATION", "accomodationId"),
    ("salaries", "Salaries", "SALARIES", "salaryId"),
    ("permits", "Permits", "PERMITS", "permitId"),
    ("other-expenses", "Other", "OTHER_EXPENSES", "otherExpensesId"),
    ("farmer-payments", "Farmer payments", "FARMER_PAYMENTS", "farmerPaymentsId"),
]


def _run(owner, fn, on_done, on_error):
    worker = Worker(fn)
    worker.signals.finished.connect(on_done)
    worker.signals.error.connect(on_error)
    owner._active_workers.append(worker)
    QThreadPool.globalInstance().start(worker)


class ExpensesTab(QWidget):
    def __init__(self, api_client, trip_id: int, on_changed, parent=None):
        super().__init__(parent)
        self._active_workers = []
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        hint = QLabel(
            "One tab per expense category. A tab showing a number has entries that are not paid or assigned yet - "
            "open it, pick the entry, then Pay it from the company wallet or Assign an incoming transaction to it."
        )
        hint.setProperty("role", "muted")
        hint.setWordWrap(True)
        layout.addWidget(hint)

        self.tabs = QTabWidget()
        layout.addWidget(self.tabs)
        self.pages: list[ExpenseCategoryPage] = []
        for key, label, target, id_key in CATEGORIES:
            page = ExpenseCategoryPage(api_client, trip_id, key, label, target, id_key, self._page_changed)
            self.pages.append(page)
            self.tabs.addTab(page, label)
        self._on_changed = on_changed
        self._trip_id = trip_id

    def _page_changed(self, page, unassigned_count: int, mutated: bool):
        index = self.pages.index(page)
        label = CATEGORIES[index][1]
        self.tabs.setTabText(index, f"{label} ({unassigned_count})" if unassigned_count else label)
        if mutated:
            self._on_changed()

    def reload_all(self):
        for page in self.pages:
            page.load()


class ExpenseCategoryPage(QWidget):
    def __init__(self, api_client, trip_id, key, label, target, id_key, on_page_changed, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.trip_id = trip_id
        self.key = key
        self.label = label
        self.target = target
        self.id_key = id_key
        self.on_page_changed = on_page_changed
        self.rows: list[dict] = []
        self.selected: dict | None = None
        self._mutated = False
        self._active_workers = []

        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 6, 0, 0)

        top = QHBoxLayout()
        top.addWidget(QLabel("Show"))
        self.filter_combo = QComboBox()
        for text, value in (("All", "all"), ("Not paid yet", "pending"), ("Paid / assigned", "assigned")):
            self.filter_combo.addItem(text, value)
        self.filter_combo.currentIndexChanged.connect(lambda _i: self._render())
        top.addWidget(self.filter_combo)
        top.addStretch()
        self.new_btn = QPushButton(f"New {label} Entry")
        self.new_btn.setProperty("role", "primary")
        self.new_btn.clicked.connect(self._new)
        top.addWidget(self.new_btn)
        layout.addLayout(top)

        self.table = DataTable(f"{label} entries", show_limit_selector=False, searchable=True, search_placeholder="Search entries")
        self.table.refresh_requested.connect(lambda _limit: self.load())
        self.table.row_clicked.connect(self._select)
        self.table.row_selected.connect(self._select)
        layout.addWidget(self.table, 1)

        actions = QHBoxLayout()
        self.selection_label = QLabel("Click an entry.")
        self.selection_label.setProperty("role", "muted")
        actions.addWidget(self.selection_label)
        actions.addStretch()
        self.pay_btn = QPushButton("Pay (Send Money)")
        self.pay_btn.setProperty("role", "primary")
        self.pay_btn.setEnabled(False)
        self.pay_btn.clicked.connect(self._pay)
        actions.addWidget(self.pay_btn)
        self.assign_btn = QPushButton("Assign Transaction")
        self.assign_btn.setEnabled(False)
        self.assign_btn.clicked.connect(self._assign)
        actions.addWidget(self.assign_btn)
        self.delete_btn = QPushButton("Delete")
        self.delete_btn.setProperty("role", "danger")
        self.delete_btn.setEnabled(False)
        self.delete_btn.clicked.connect(self._delete)
        actions.addWidget(self.delete_btn)
        layout.addLayout(actions)

        self.load()

    # ---- data ---------------------------------------------------------

    def load(self):
        self.table.set_loading()
        _run(
            self,
            lambda: self.api_client.get(f"/trips/{self.trip_id}/expenses/{self.key}"),
            self._loaded,
            self.table.set_error,
        )

    def _loaded(self, rows):
        self.rows = rows or []
        self._render()
        mutated, self._mutated = self._mutated, False
        self.on_page_changed(self, sum(1 for r in self.rows if not r.get("financialTransactionId")), mutated)

    @staticmethod
    def _is_paid(row: dict) -> bool:
        return bool(row.get("financialTransactionId"))

    def _render(self):
        mode = self.filter_combo.currentData()
        shown = [
            r for r in self.rows
            if mode == "all" or (mode == "pending" and not self._is_paid(r)) or (mode == "assigned" and self._is_paid(r))
        ]
        shaped = [{"state": "PAID" if self._is_paid(r) else "NOT PAID", **r} for r in shown]
        self.table.set_rows(shaped, row_color=lambda row: None if row.get("state") == "PAID" else UNASSIGNED_TINT)
        self.selected = None
        self._sync_buttons()

    # ---- selection ----------------------------------------------------

    def _select(self, row: dict):
        self.selected = row
        paid = row.get("state") == "PAID"
        self.selection_label.setText(
            f"Selected: {self.label} #{row.get(self.id_key)} - KES {row.get('amount')} - "
            + ("already paid/assigned" if paid else "NOT paid yet: Pay it or Assign a transaction")
        )
        self._sync_buttons()

    def _sync_buttons(self):
        open_entry = self.selected is not None and self.selected.get("state") != "PAID"
        for button in (self.pay_btn, self.assign_btn, self.delete_btn):
            button.setEnabled(open_entry)
        if self.selected is None:
            self.selection_label.setText("Click an entry.")

    def _row_id(self):
        return self.selected.get(self.id_key) if self.selected else None

    def _changed(self):
        # The trip's totals are refreshed once the new list has actually loaded.
        self._mutated = True
        self.load()

    # ---- create -------------------------------------------------------

    def _new(self):
        # The pickers (vehicles, machines, farmers) are fetched first so the
        # form opens with real choices instead of asking for ids.
        needs = {"transport": "/vehicles", "machine-expenses": "/machines", "farmer-payments": "/farmers"}.get(self.key)
        if needs is None:
            self._open_form([])
            return
        set_busy(self.new_btn, True, "Loading")

        def done(options):
            set_busy(self.new_btn, False)
            self._open_form(options or [])

        def failed(message):
            set_busy(self.new_btn, False)
            QMessageBox.critical(self, "Couldn't load the choices", message)

        _run(self, lambda: self.api_client.get(needs), done, failed)

    def _fields(self, options: list) -> list[FieldSpec]:
        fields = [FieldSpec("amount", "Amount (KES)", type="float", required=True, maximum=100_000_000)]
        if self.key == "farmer-payments":
            fields.append(FieldSpec(
                "farmerId", "Farmer", type="combo", required=True,
                options=[(o.get("name") or f"Farmer #{o.get('farmerId')}", o.get("farmerId")) for o in options],
            ))
            fields.append(FieldSpec("method", "Method", placeholder="e.g. M-Pesa"))
        if self.key == "transport":
            fields.append(FieldSpec(
                "vehicleUniqueId", "Vehicle", type="combo",
                options=[("- not in the list -", None)] + [(o.get("uniqueIdentifier"), o.get("uniqueIdentifier")) for o in options],
            ))
            fields.append(FieldSpec("additionalId", "Plate / number (if not in the list)"))
        if self.key == "machine-expenses":
            fields.append(FieldSpec(
                "machineId", "Machine", type="combo",
                options=[("- none -", None)] + [(o.get("name"), o.get("machineId")) for o in options],
            ))
            fields.append(FieldSpec("serialNo", "Serial number"))
        if self.key in ("transport", "communications", "machine-expenses", "accomodation", "salaries"):
            fields.append(FieldSpec("type", "Payment mode", type="combo", options=PAYMENT_MODES))
        if self.key == "accomodation":
            fields.append(FieldSpec("startDate", "From", type="date"))
            fields.append(FieldSpec("endDate", "To", type="date"))
        fields.append(FieldSpec("description", "Description", type="textarea"))
        return fields

    def _open_form(self, options: list):
        dialog = RecordFormDialog(
            f"New {self.label} Entry",
            self._fields(options),
            save_fn=lambda values: self.api_client.post(f"/trips/{self.trip_id}/expenses/{self.key}", json=values, actor=True),
            parent=self,
        )
        if dialog.exec():
            self._changed()

    # ---- pay / assign / delete ---------------------------------------

    def _pay(self):
        row_id = self._row_id()
        if row_id is None:
            return
        dialog = PayoutDialog(
            self.api_client,
            f"Pay {self.label} #{row_id} - Send Money",
            purpose=f"{self.label} #{row_id}",
            recipient_target=self.target,
            recipient_row_id=row_id,
            send_fn=lambda amount, phone: self.api_client.post(
                "/transactions/send-money",
                json={"amount": amount, "phoneNumber": phone, "target": self.target, "targetRowId": row_id},
                actor=True,
                timeout=85,
            ),
            prefilled_amount=self.selected.get("amount"),
            parent=self,
        )
        dialog.exec()
        self._changed()

    def _assign(self):
        row_id = self._row_id()
        if row_id is None:
            return
        dialog = AssignTransactionDialog(self.api_client, self.label, self.target, row_id, self.selected.get("amount"), parent=self)
        if dialog.exec():
            self._changed()

    def _delete(self):
        row_id = self._row_id()
        if row_id is None:
            return
        answer = QMessageBox.question(
            self, "Delete entry", f"Delete {self.label} #{row_id}? It hasn't been paid or assigned.",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.Cancel, QMessageBox.StandardButton.Cancel,
        )
        if answer != QMessageBox.StandardButton.Yes:
            return
        set_busy(self.delete_btn, True, "Deleting")

        def done(_result):
            set_busy(self.delete_btn, False)
            self._changed()

        def failed(message):
            set_busy(self.delete_btn, False)
            QMessageBox.critical(self, "Couldn't delete", message)

        _run(self, lambda: self.api_client.delete(f"/trips/{self.trip_id}/expenses/{self.key}/{row_id}", actor=True), done, failed)


class AssignTransactionDialog(QDialog):
    """Link an expense entry to money that has already come in: the
    unassigned transactions, the ones matching the entry's amount first."""

    def __init__(self, api_client, label: str, target: str, row_id: int, amount, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.target = target
        self.row_id = row_id
        self._active_workers = []
        self.setWindowTitle(f"Assign a transaction to {label} #{row_id}")
        self.resize(640, 420)

        layout = QVBoxLayout(self)
        hint = QLabel(
            f"Transactions not yet assigned to anything, closest to KES {amount} first. Double-click the one that pays this entry."
        )
        hint.setProperty("role", "muted")
        hint.setWordWrap(True)
        layout.addWidget(hint)
        self.table = DataTable("Unassigned transactions", show_limit_selector=False)
        self.table.refresh_requested.connect(lambda _limit: self._load(amount))
        self.table.row_selected.connect(self._pick)
        layout.addWidget(self.table)
        self.error_label = QLabel("")
        self.error_label.setProperty("role", "error")
        self.error_label.setWordWrap(True)
        layout.addWidget(self.error_label)
        self._load(amount)

    def _load(self, amount):
        self.table.set_loading()

        def done(rows):
            def closeness(r):
                try:
                    return abs(float(r.get("amount") or 0) - float(amount or 0))
                except (TypeError, ValueError):
                    return 0
            self.table.set_rows(sorted(rows or [], key=closeness))

        _run(self, lambda: self.api_client.get("/transactions/unassigned"), done, self.table.set_error)

    def _pick(self, transaction: dict):
        ft_id = transaction.get("financialTransactionId")
        answer = QMessageBox.question(
            self, "Assign transaction?",
            f"Assign transaction #{ft_id} (KES {transaction.get('amount')}) to this entry?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.Cancel, QMessageBox.StandardButton.Cancel,
        )
        if answer != QMessageBox.StandardButton.Yes:
            return
        self.error_label.setText("")
        _run(
            self,
            lambda: self.api_client.post(
                f"/transactions/{ft_id}/assign", json={"target": self.target, "targetRowId": self.row_id}, actor=True
            ),
            lambda _r: self.accept(),
            lambda message: self.error_label.setText(message),
        )
