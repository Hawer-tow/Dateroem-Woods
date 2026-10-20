import base64
import mimetypes

from PySide6.QtCore import QThreadPool, QTimer, Qt
from PySide6.QtGui import QColor
from PySide6.QtWidgets import (
    QCheckBox,
    QDialog,
    QFileDialog,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QMessageBox,
    QPushButton,
    QSplitter,
    QStackedWidget,
    QTabWidget,
    QVBoxLayout,
    QWidget,
)

from app.core.worker import Worker
from app.ui.sections.expenses_tab import ExpensesTab
from app.ui.sections.loan_detail_dialog import LoanDetailDialog, money
from app.ui.sections.trip_finalize_dialog import FinalizeTripDialog
from app.ui.icons import icon
from app.ui.widgets.busy import set_busy, set_spinning
from app.ui.widgets.data_table import CellNote, DataTable
from app.ui.widgets.payout_dialog import PayoutDialog
from app.ui.widgets.record_form_dialog import FieldSpec, RecordFormDialog

# Orders that missed a previous delivery - shown in a distinct colour in the
# pick list so they get reassigned first.
PRIORITY_TINT = QColor("#8e44ad")
PRIORITY_TINT.setAlpha(120)

LIGHT_STATUS_OPTIONS = [("Pending", "PENDING"), ("Delivered", "DELIVERED"), ("Cancelled", "CANCELLED")]


class TripDetailDialog(QDialog):
    """A trip's full picture in one place - summary, loans (with the real
    Send Money payout), and both delivery types (with the status/delete
    actions those sections need) - since none of this makes sense as a
    top-level sidebar section without a trip already chosen, mirroring how
    the mobile app itself is trip-scoped."""

    def __init__(self, api_client, trip: dict, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.trip = trip
        trip_id = trip.get("tripId")
        self.trip_id = trip_id
        self.selected_loan: dict | None = None
        self.selected_light: dict | None = None
        self.selected_bulk: dict | None = None
        self.selected_tree: dict | None = None
        self.farmers: list[dict] = []
        self.remainder_trees: list[dict] = []
        self.vehicles: list[dict] = []
        self.light_rows: list[dict] = []
        self.selected_capital: dict | None = None
        self.selected_machine: dict | None = None
        self.selected_vehicle: dict | None = None
        self.selected_light_order: dict | None = None
        self.selected_bulk_order: dict | None = None
        self.bulk_rows: list[dict] = []
        # See GenericListSection - keeps in-flight Workers from being
        # garbage-collected before their signals fire.
        self._active_workers = []

        self.setWindowTitle(f"Trip #{trip_id} - {trip.get('productDescription') or 'Details'}")
        self.resize(1000, 760)

        layout = QVBoxLayout(self)
        header = QLabel(f"Trip #{trip_id}   {trip.get('startDate', '?')} → {trip.get('endDate') or 'ongoing'}")
        header.setProperty("role", "title")
        layout.addWidget(header)

        tabs = QTabWidget()
        layout.addWidget(tabs)

        self.summary_label = QLabel("Loading...")
        self.summary_label.setProperty("role", "muted")
        self.summary_label.setWordWrap(True)
        summary_layout = QVBoxLayout()
        summary_layout.addWidget(self.summary_label)

        # "in trip allow editing since an admin user can even edit this" -
        # end date, delivery count, body feet, description.
        summary_actions = QHBoxLayout()
        summary_actions.addStretch()
        self.edit_trip_btn = QPushButton("Edit Trip")
        self.edit_trip_btn.setProperty("role", "primary")
        self.edit_trip_btn.clicked.connect(self._open_edit_trip)
        self.refresh_all_btn = QPushButton("Refresh")
        self.refresh_all_btn.setIcon(icon("refresh"))
        self.refresh_all_btn.clicked.connect(self._refresh_all)
        summary_actions.addWidget(self.refresh_all_btn)
        summary_actions.addWidget(self.edit_trip_btn)
        # "you can never create a new trip before you finalize every detail in
        # the other trip" - the one way a trip gets closed.
        self.finalize_trip_btn = QPushButton("Finalize Trip")
        self.finalize_trip_btn.setProperty("role", "primary")
        self.finalize_trip_btn.clicked.connect(self._open_finalize)
        summary_actions.addWidget(self.finalize_trip_btn)
        self._sync_finalize_button()
        # "a trip can be deleted and this will force all the assigned orders
        # back to the unassigned ones but this time not given priority".
        self.delete_trip_btn = QPushButton("Delete Trip")
        self.delete_trip_btn.setProperty("role", "danger")
        self.delete_trip_btn.clicked.connect(self._delete_trip)
        summary_actions.addWidget(self.delete_trip_btn)
        summary_layout.addLayout(summary_actions)
        summary_layout.addStretch()

        summary_widget = QWidget()
        summary_widget.setLayout(summary_layout)
        tabs.addTab(summary_widget, "Summary")

        loans_widget = QWidget()
        loans_layout = QVBoxLayout(loans_widget)
        loans_layout.setContentsMargins(0, 0, 0, 0)
        self.loans_table = DataTable("Loans", show_limit_selector=False)
        self.loans_table.row_clicked.connect(self._on_loan_selected)
        self.loans_table.row_selected.connect(self._open_loan_details)
        loans_layout.addWidget(self.loans_table)

        loans_actions = QHBoxLayout()
        self.loan_selection_label = QLabel("Click a loan to see paid and remainder; double-click for its details.")
        self.loan_selection_label.setProperty("role", "muted")
        loans_actions.addWidget(self.loan_selection_label)
        loans_actions.addStretch()
        self.new_loan_btn = QPushButton("New Loan")
        self.new_loan_btn.setProperty("role", "primary")
        self.new_loan_btn.clicked.connect(self._open_create_loan)
        loans_actions.addWidget(self.new_loan_btn)
        self.edit_loan_btn = QPushButton("Edit Loan")
        self.edit_loan_btn.setEnabled(False)
        self.edit_loan_btn.clicked.connect(self._open_edit_loan)
        loans_actions.addWidget(self.edit_loan_btn)
        self.repayment_btn = QPushButton("Record Repayment")
        self.repayment_btn.setEnabled(False)
        self.repayment_btn.clicked.connect(self._open_repayment)
        loans_actions.addWidget(self.repayment_btn)
        self.loan_details_btn = QPushButton("Loan Details")
        self.loan_details_btn.setEnabled(False)
        self.loan_details_btn.clicked.connect(lambda: self._open_loan_details(self.selected_loan))
        loans_actions.addWidget(self.loan_details_btn)
        self.pay_loan_btn = QPushButton("Pay Loan (Send Money)")
        self.pay_loan_btn.setProperty("role", "primary")
        self.pay_loan_btn.setEnabled(False)
        self.pay_loan_btn.clicked.connect(self._open_pay_loan)
        loans_actions.addWidget(self.pay_loan_btn)
        loans_layout.addLayout(loans_actions)
        tabs.addTab(loans_widget, "Loans")

        # "add the finances one had at the start of the trip" - the cash
        # split (mirrors mobile's Capital screen) plus actually recording
        # CAPITAL entries, which no client could do before this.
        capital_widget = QWidget()
        capital_layout = QVBoxLayout(capital_widget)
        capital_layout.setContentsMargins(0, 0, 0, 0)

        cash_split_row = QHBoxLayout()
        self.cash_split_label = QLabel("Loading...")
        self.cash_split_label.setProperty("role", "muted")
        cash_split_row.addWidget(self.cash_split_label)
        cash_split_row.addStretch()
        self.edit_cash_split_btn = QPushButton("Edit Cash Split")
        self.edit_cash_split_btn.clicked.connect(self._open_edit_cash_split)
        cash_split_row.addWidget(self.edit_cash_split_btn)
        self.recalc_total_btn = QPushButton("Recalculate Total Payment")
        self.recalc_total_btn.clicked.connect(self._recalculate_total_payment)
        cash_split_row.addWidget(self.recalc_total_btn)
        capital_layout.addLayout(cash_split_row)

        self.capital_table = DataTable("Capital", show_limit_selector=False)
        capital_layout.addWidget(self.capital_table)

        capital_actions = QHBoxLayout()
        self.capital_hint_label = QLabel("Money put into this trip as capital (not a loan).")
        self.capital_hint_label.setProperty("role", "muted")
        capital_actions.addWidget(self.capital_hint_label)
        capital_actions.addStretch()
        self.new_capital_btn = QPushButton("New Capital Entry")
        self.new_capital_btn.setProperty("role", "primary")
        self.new_capital_btn.clicked.connect(self._open_create_capital)
        capital_actions.addWidget(self.new_capital_btn)
        self.edit_capital_btn = QPushButton("Edit Entry")
        self.edit_capital_btn.setEnabled(False)
        self.edit_capital_btn.clicked.connect(self._open_edit_capital)
        capital_actions.addWidget(self.edit_capital_btn)
        self.capital_table.row_selected.connect(self._on_capital_selected)
        self.capital_table.row_clicked.connect(self._on_capital_selected)
        capital_layout.addLayout(capital_actions)
        tabs.addTab(capital_widget, "Capital")

        # "combine the bulk and light delivery to one section allow
        # toggle between the 2" - one tab, a toggle (same pattern as
        # Users/Farmers) switching a QStackedWidget between the two tables
        # instead of two separate tabs.
        deliveries_widget = QWidget()
        deliveries_layout = QVBoxLayout(deliveries_widget)
        deliveries_layout.setContentsMargins(0, 0, 0, 0)

        delivery_toggle_row = QHBoxLayout()
        self.light_toggle_btn = QPushButton("Light Deliveries")
        self.light_toggle_btn.setProperty("role", "primary")
        self.light_toggle_btn.clicked.connect(lambda: self._switch_delivery_view(0))
        delivery_toggle_row.addWidget(self.light_toggle_btn)
        self.bulk_toggle_btn = QPushButton("Bulk Deliveries")
        self.bulk_toggle_btn.clicked.connect(lambda: self._switch_delivery_view(1))
        delivery_toggle_row.addWidget(self.bulk_toggle_btn)
        delivery_toggle_row.addStretch()
        deliveries_layout.addLayout(delivery_toggle_row)

        self.deliveries_stack = QStackedWidget()
        deliveries_layout.addWidget(self.deliveries_stack)

        light_widget = QWidget()
        light_layout = QVBoxLayout(light_widget)
        light_layout.setContentsMargins(0, 0, 0, 0)
        self.light_table = DataTable("Light Deliveries", show_limit_selector=False)
        self.light_table.row_selected.connect(self._on_light_selected)
        self.light_table.row_clicked.connect(self._on_light_selected)

        # "you go in it and we can have one or more orders" - a delivery is
        # one row; the orders riding on it are listed here, not as extra
        # deliveries in the table above.
        self.light_orders_table = DataTable("Orders in this delivery", show_limit_selector=False)
        self.light_orders_table.setMinimumHeight(130)
        self.light_orders_table.row_selected.connect(self._on_light_order_selected)
        self.light_orders_table.row_clicked.connect(self._on_light_order_selected)
        # Deliveries on top, the selected delivery's orders below - a draggable
        # split instead of a fixed-height strip under a big table.
        light_split = QSplitter(Qt.Orientation.Vertical)
        light_split.setChildrenCollapsible(False)
        self.light_table.setMinimumHeight(110)
        light_split.addWidget(self.light_table)
        light_split.addWidget(self.light_orders_table)
        light_split.setSizes([250, 250])
        light_layout.addWidget(light_split, 1)

        light_order_row = QHBoxLayout()
        self.light_order_label = QLabel("Click an order above to mark just that one delivered.")
        self.light_order_label.setProperty("role", "muted")
        light_order_row.addWidget(self.light_order_label)
        light_order_row.addStretch()
        self.light_order_delivered_btn = QPushButton("Mark Order Delivered")
        self.light_order_delivered_btn.setEnabled(False)
        self.light_order_delivered_btn.clicked.connect(lambda: self._mark_order_delivered("light"))
        light_order_row.addWidget(self.light_order_delivered_btn)
        light_layout.addLayout(light_order_row)

        light_actions = QHBoxLayout()
        self.light_selection_label = QLabel("Double-click a delivery to select it.")
        self.light_selection_label.setProperty("role", "muted")
        light_actions.addWidget(self.light_selection_label)
        light_actions.addStretch()
        # "in desktop in both deliveries allow one to create one"
        self.new_light_btn = QPushButton("New Light Delivery")
        self.new_light_btn.setProperty("role", "primary")
        self.new_light_btn.clicked.connect(self._open_create_light_delivery)
        light_actions.addWidget(self.new_light_btn)
        self.light_status_btn = QPushButton("Update Status")
        self.light_status_btn.setEnabled(False)
        self.light_status_btn.clicked.connect(self._open_light_status)
        light_actions.addWidget(self.light_status_btn)
        # Marking delivered asks which orders actually arrived - the rest
        # drop back to the pending pool as high priority.
        self.light_complete_btn = QPushButton("Complete Delivery")
        self.light_complete_btn.setEnabled(False)
        self.light_complete_btn.clicked.connect(self._open_complete_light)
        light_actions.addWidget(self.light_complete_btn)
        # "when i click that order then from there i can get the assign
        # pending order" - tied to the selected delivery, so the order
        # is linked to that specific delivery, not just the trip.
        self.assign_light_btn = QPushButton("Assign Pending Order")
        self.assign_light_btn.setEnabled(False)
        self.assign_light_btn.clicked.connect(lambda: self._open_assign_pending(parent_light=self.selected_light))
        light_actions.addWidget(self.assign_light_btn)
        light_layout.addLayout(light_actions)
        self.deliveries_stack.addWidget(light_widget)

        bulk_widget = QWidget()
        bulk_layout = QVBoxLayout(bulk_widget)
        bulk_layout.setContentsMargins(0, 0, 0, 0)
        self.bulk_table = DataTable("Bulk Deliveries", show_limit_selector=False)
        self.bulk_table.row_selected.connect(self._on_bulk_selected)
        self.bulk_table.row_clicked.connect(self._on_bulk_selected)

        self.bulk_orders_table = DataTable("Side quests riding with this delivery", show_limit_selector=False)
        self.bulk_orders_table.setMinimumHeight(120)
        self.bulk_orders_table.row_selected.connect(self._on_bulk_order_selected)
        self.bulk_orders_table.row_clicked.connect(self._on_bulk_order_selected)
        bulk_split = QSplitter(Qt.Orientation.Vertical)
        bulk_split.setChildrenCollapsible(False)
        self.bulk_table.setMinimumHeight(110)
        bulk_split.addWidget(self.bulk_table)
        bulk_split.addWidget(self.bulk_orders_table)
        bulk_split.setSizes([250, 220])
        bulk_layout.addWidget(bulk_split, 1)

        bulk_order_row = QHBoxLayout()
        self.bulk_order_label = QLabel("Click a side quest above to mark just that one delivered.")
        self.bulk_order_label.setProperty("role", "muted")
        bulk_order_row.addWidget(self.bulk_order_label)
        bulk_order_row.addStretch()
        self.bulk_order_delivered_btn = QPushButton("Mark Side Quest Delivered")
        self.bulk_order_delivered_btn.setEnabled(False)
        self.bulk_order_delivered_btn.clicked.connect(lambda: self._mark_order_delivered("bulk"))
        bulk_order_row.addWidget(self.bulk_order_delivered_btn)
        bulk_layout.addLayout(bulk_order_row)

        bulk_actions = QHBoxLayout()
        self.bulk_selection_label = QLabel("Double-click an order to select it.")
        self.bulk_selection_label.setProperty("role", "muted")
        bulk_actions.addWidget(self.bulk_selection_label)
        bulk_actions.addStretch()
        # "add a BULK_TRIP_PRODUCTION_DESCRIPTION section to add data
        # concerning that BULK_DELIVERY" - dimension/measurement line items
        # for this specific bulk order (e.g. "6*6*4 5*6*9 ..." plus a body
        # feet total), separate from Finalize's own one-row side effect.
        self.new_bulk_btn = QPushButton("New Bulk Delivery")
        self.new_bulk_btn.setProperty("role", "primary")
        self.new_bulk_btn.clicked.connect(self._open_create_bulk_delivery)
        bulk_actions.addWidget(self.new_bulk_btn)
        # "a light delivery can be fixed to a bulk delivery as a side
        # quest ... since bulk delivery still has space to do this or
        # time" - a small light order that rides along with this bulk run.
        self.bulk_side_quest_btn = QPushButton("Add Side Quest")
        self.bulk_side_quest_btn.setEnabled(False)
        self.bulk_side_quest_btn.clicked.connect(lambda: self._open_assign_pending(bulk=self.selected_bulk))
        bulk_actions.addWidget(self.bulk_side_quest_btn)
        self.bulk_results_btn = QPushButton("Side Quest Results")
        self.bulk_results_btn.setEnabled(False)
        self.bulk_results_btn.clicked.connect(self._open_complete_bulk_side_quests)
        bulk_actions.addWidget(self.bulk_results_btn)
        self.bulk_production_btn = QPushButton("Production Description")
        self.bulk_production_btn.setEnabled(False)
        self.bulk_production_btn.clicked.connect(self._open_bulk_production)
        bulk_actions.addWidget(self.bulk_production_btn)
        # "once the bulk delivery is delivered the details are filled on
        # amount cash soft" - the one control that actually sets Amount_Paid,
        # which is what the trip's profit is computed from (Production
        # Description's Product_Total never feeds it). Missing entirely
        # before, which is why filling in an amount there never moved
        # profit. Re-openable afterward too, to fix a wrong figure.
        self.bulk_payment_btn = QPushButton("Record Payment")
        self.bulk_payment_btn.setProperty("role", "primary")
        self.bulk_payment_btn.setEnabled(False)
        self.bulk_payment_btn.clicked.connect(self._open_bulk_payment)
        bulk_actions.addWidget(self.bulk_payment_btn)
        self.bulk_delete_btn = QPushButton("Delete Order")
        self.bulk_delete_btn.setProperty("role", "danger")
        self.bulk_delete_btn.setEnabled(False)
        self.bulk_delete_btn.clicked.connect(self._delete_bulk_order)
        bulk_actions.addWidget(self.bulk_delete_btn)
        bulk_layout.addLayout(bulk_actions)
        self.deliveries_stack.addWidget(bulk_widget)

        tabs.addTab(deliveries_widget, "Deliveries")

        # "in the trip add a tab expenses in it add all the expenses as tabs" -
        # expenses are trip-scoped, so they live here: one tab per category,
        # unpaid entries flagged, each one payable or assignable in place.
        self.expenses_tab = ExpensesTab(self.api_client, self.trip_id, on_changed=self._on_expenses_changed)
        tabs.addTab(self.expenses_tab, "Expenses")

        trees_widget = QWidget()
        trees_layout = QVBoxLayout(trees_widget)
        trees_layout.setContentsMargins(0, 0, 0, 0)
        self.trees_table = DataTable("Trees", show_limit_selector=False, searchable=True, search_placeholder="Search trees by farmer or type")
        self.trees_table.row_selected.connect(self._on_tree_selected)
        trees_layout.addWidget(self.trees_table)

        trees_actions = QHBoxLayout()
        self.tree_selection_label = QLabel("Double-click a tree registration to select it.")
        self.tree_selection_label.setProperty("role", "muted")
        trees_actions.addWidget(self.tree_selection_label)
        trees_actions.addStretch()
        self.new_tree_btn = QPushButton("New Tree")
        self.new_tree_btn.setProperty("role", "primary")
        self.new_tree_btn.clicked.connect(self._open_create_tree)
        trees_actions.addWidget(self.new_tree_btn)
        self.edit_tree_btn = QPushButton("Edit")
        self.edit_tree_btn.setEnabled(False)
        self.edit_tree_btn.clicked.connect(self._open_edit_tree)
        trees_actions.addWidget(self.edit_tree_btn)
        # "allow a place where we tick there were remainders for admin
        # when finalizing a trip ... linking a specific tree ... is
        # better since one can use this to detect even the exact
        # location of the remainder trees" - records a REMAINDER_TREES
        # row tied to whichever tree on this trip is selected.
        self.record_remainder_btn = QPushButton("Record Remainder")
        self.record_remainder_btn.setEnabled(False)
        self.record_remainder_btn.clicked.connect(self._open_record_remainder)
        trees_actions.addWidget(self.record_remainder_btn)
        trees_layout.addLayout(trees_actions)
        tabs.addTab(trees_widget, "Trees")

        # "eliminate machine id since ids are not shown to the user and
        # machine should be selected from the machine table ... in the
        # desktop in trip add the tab machinery to allow one to add and
        # vehicles to allow one to see the vehicles" / "adding of a new
        # machine or vehicle is only allowed in the desktop" - these two
        # registries aren't themselves trip-scoped (no Trip_ID column),
        # but this is where an admin actually needs to add one - right
        # where they're about to record a machine/transport expense.
        machinery_widget = QWidget()
        machinery_layout = QVBoxLayout(machinery_widget)
        machinery_layout.setContentsMargins(0, 0, 0, 0)
        self.machinery_table = DataTable("Machinery", show_limit_selector=False)
        machinery_layout.addWidget(self.machinery_table)

        machinery_actions = QHBoxLayout()
        machinery_actions.addStretch()
        self.new_machine_btn = QPushButton("New Machine")
        self.new_machine_btn.setProperty("role", "primary")
        self.new_machine_btn.clicked.connect(self._open_create_machine)
        machinery_actions.addWidget(self.new_machine_btn)
        self.edit_machine_btn = QPushButton("Edit Machine")
        self.edit_machine_btn.setEnabled(False)
        self.edit_machine_btn.clicked.connect(self._open_edit_machine)
        machinery_actions.addWidget(self.edit_machine_btn)
        self.machine_selection_label = QLabel("Click a machine to edit it.")
        self.machine_selection_label.setProperty("role", "muted")
        machinery_actions.insertWidget(0, self.machine_selection_label)
        self.machinery_table.row_selected.connect(self._on_machine_selected)
        self.machinery_table.row_clicked.connect(self._on_machine_selected)
        machinery_layout.addLayout(machinery_actions)
        tabs.addTab(machinery_widget, "Machinery")

        vehicles_widget = QWidget()
        vehicles_layout = QVBoxLayout(vehicles_widget)
        vehicles_layout.setContentsMargins(0, 0, 0, 0)
        self.vehicles_table = DataTable("Vehicles", show_limit_selector=False)
        vehicles_layout.addWidget(self.vehicles_table)

        vehicles_actions = QHBoxLayout()
        vehicles_actions.addStretch()
        self.new_vehicle_btn = QPushButton("New Vehicle")
        self.new_vehicle_btn.setProperty("role", "primary")
        self.new_vehicle_btn.clicked.connect(self._open_create_vehicle)
        vehicles_actions.addWidget(self.new_vehicle_btn)
        self.edit_vehicle_btn = QPushButton("Edit Vehicle")
        self.edit_vehicle_btn.setEnabled(False)
        self.edit_vehicle_btn.clicked.connect(self._open_edit_vehicle)
        vehicles_actions.addWidget(self.edit_vehicle_btn)
        self.vehicle_selection_label = QLabel("Click a vehicle to edit it.")
        self.vehicle_selection_label.setProperty("role", "muted")
        vehicles_actions.insertWidget(0, self.vehicle_selection_label)
        self.vehicles_table.row_selected.connect(self._on_vehicle_selected)
        self.vehicles_table.row_clicked.connect(self._on_vehicle_selected)
        vehicles_layout.addLayout(vehicles_actions)
        tabs.addTab(vehicles_widget, "Vehicles")

        # Every table's Refresh button re-runs that table's own loader (they
        # were never connected before, so Refresh did nothing in here).
        self.loans_table.refresh_requested.connect(lambda _limit: self._load_loans())
        self.capital_table.refresh_requested.connect(lambda _limit: (self._load_capital(), self._load_summary()))
        for delivery_table in (self.light_table, self.bulk_table, self.light_orders_table, self.bulk_orders_table):
            delivery_table.refresh_requested.connect(lambda _limit: self._load_deliveries())
        self.trees_table.refresh_requested.connect(lambda _limit: self._load_trees())
        self.machinery_table.refresh_requested.connect(lambda _limit: self._load_machinery())
        self.vehicles_table.refresh_requested.connect(lambda _limit: self._load_vehicles())

        self._load_summary()
        self._load_loans()
        self._load_capital()
        self._load_deliveries()
        self._load_trees()
        self._load_farmers_and_remainders()
        self._load_machinery()
        self._load_vehicles()

    def _run(self, fn, on_done, on_error):
        worker = Worker(fn)
        worker.signals.finished.connect(on_done)
        worker.signals.error.connect(on_error)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)

    def _switch_delivery_view(self, index: int):
        self.deliveries_stack.setCurrentIndex(index)
        self.light_toggle_btn.setProperty("role", "primary" if index == 0 else "")
        self.bulk_toggle_btn.setProperty("role", "primary" if index == 1 else "")
        for btn in (self.light_toggle_btn, self.bulk_toggle_btn):
            btn.style().unpolish(btn)
            btn.style().polish(btn)

    def _load_summary(self):
        self._run(
            lambda: self.api_client.get(f"/trips/{self.trip_id}/summary"),
            self._on_summary,
            lambda msg: self.summary_label.setText(f"Couldn't load summary: {msg}"),
        )

    def _on_summary(self, data: dict):
        lines = [f"{key}: {value}" for key, value in (data or {}).items()]
        self.summary_label.setText("\n".join(lines) if lines else "No summary data.")

    def _refresh_all(self):
        set_spinning(self.refresh_all_btn, True)
        safety = QTimer(self.refresh_all_btn)
        safety.setSingleShot(True)
        safety.timeout.connect(lambda: set_spinning(self.refresh_all_btn, False))
        safety.start(1500)
        self._load_summary()
        self._load_loans()
        self._load_capital()
        self._load_deliveries()
        self._load_trees()
        self._load_machinery()
        self._load_vehicles()
        self.expenses_tab.reload_all()

    def _delete_trip(self):
        answer = QMessageBox.question(
            self,
            "Delete trip",
            f"Delete Trip #{self.trip_id}?\n\nEvery order still assigned to it goes back to the pending list "
            "(without priority - the delivery may have been made by mistake). Orders already delivered keep "
            "their record.",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.Cancel,
            QMessageBox.StandardButton.Cancel,
        )
        if answer != QMessageBox.StandardButton.Yes:
            return
        set_busy(self.delete_trip_btn, True, "Deleting")

        def done(_result):
            set_busy(self.delete_trip_btn, False)
            self.accept()

        def failed(message: str):
            set_busy(self.delete_trip_btn, False)
            QMessageBox.critical(self, "Couldn't delete the trip", message)

        self._run(lambda: self.api_client.delete(f"/trips/{self.trip_id}", actor=True), done, failed)

    def _sync_finalize_button(self):
        end = self.trip.get("endDate")
        self.finalize_trip_btn.setEnabled(not end)
        self.finalize_trip_btn.setText(f"Finalized {str(end)[:10]}" if end else "Finalize Trip")

    def _open_finalize(self):
        dialog = FinalizeTripDialog(self.api_client, self.trip_id, parent=self)
        if dialog.exec():
            # Pull the trip again so its end date (and this dialog) reflect it.
            def on_trip(trip):
                self.trip = trip or self.trip
                self._sync_finalize_button()
                self._load_summary()
                self._load_deliveries()
                self._load_loans()

            self._run(lambda: self.api_client.get(f"/trips/{self.trip_id}"), on_trip, lambda _m: None)

    def _open_edit_trip(self):
        # An open trip is closed only by Finalize Trip, so the end date is
        # editable only once it has one.
        fields = ([FieldSpec("endDate", "End date", type="date")] if self.trip.get("endDate") else []) + [
            FieldSpec("noOfDeliveries", "Number of deliveries", type="int", maximum=1_000_000),
            FieldSpec("totalBodyFeet", "Total body feet", type="float", maximum=1_000_000),
            FieldSpec("productDescription", "Product description", type="textarea"),
        ]
        dialog = RecordFormDialog(
            f"Edit Trip #{self.trip_id}",
            fields,
            save_fn=lambda values: self.api_client.patch(f"/trips/{self.trip_id}", json=values),
            initial_values=self.trip,
            parent=self,
        )
        if dialog.exec():
            self._load_summary()

    def _load_loans(self):
        self._run(
            lambda: self.api_client.get(f"/trips/{self.trip_id}/finance/loans"),
            lambda rows: self.loans_table.set_rows(rows or []),
            self.loans_table.set_error,
        )

    def _load_deliveries(self):
        def work():
            return self.api_client.get(f"/trips/{self.trip_id}/deliveries")

        def on_done(data: dict):
            self.light_rows = (data or {}).get("light") or []
            self.bulk_rows = (data or {}).get("bulk") or []
            # Only top-level deliveries in the main table - orders that were
            # assigned into a delivery live inside it (panel below), so
            # assigning never looks like it created another delivery.
            heads = [r for r in self.light_rows if not r.get("parentLightDeliveryId") and not r.get("bulkDeliveryId")]
            self.light_table.set_rows(self._numbered(heads))
            self.bulk_table.set_rows(self._numbered(self.bulk_rows))
            pending_light_ids = {r.get("lightDeliveryId") for r in self.light_rows if r.get("status") != "DELIVERED"}
            if self.selected_light_order and self.selected_light_order.get("lightDeliveryId") not in pending_light_ids:
                self.selected_light_order = None
            if self.selected_bulk_order and self.selected_bulk_order.get("lightDeliveryId") not in pending_light_ids:
                self.selected_bulk_order = None
            self._refresh_light_orders()
            self._refresh_bulk_orders()
            self._sync_light_buttons()
            self._sync_bulk_buttons()

        self._run(work, on_done, lambda msg: (self.light_table.set_error(msg), self.bulk_table.set_error(msg)))

    @staticmethod
    def _numbered(rows: list[dict]) -> list[dict]:
        # Tidy 1..N running numbers for identification, oldest first.
        return [{"#": i, **row} for i, row in enumerate(rows, start=1)]

    # Orders already marked delivered individually drop off these lists -
    # only what's still pending on a delivery is shown (and settled).
    def _light_children(self, head: dict | None) -> list[dict]:
        if not head:
            return []
        head_id = head.get("lightDeliveryId")
        return [
            r for r in self.light_rows
            if r.get("parentLightDeliveryId") == head_id and r.get("status") != "DELIVERED"
        ]

    def _bulk_children(self, bulk: dict | None) -> list[dict]:
        if not bulk:
            return []
        bulk_id = bulk.get("bulkDeliveryId")
        return [
            r for r in self.light_rows
            if r.get("bulkDeliveryId") == bulk_id and r.get("status") != "DELIVERED"
        ]

    def _refresh_light_orders(self):
        children = self._light_children(self.selected_light)
        self.light_orders_table.set_rows(self._numbered(children))

    def _refresh_bulk_orders(self):
        children = self._bulk_children(self.selected_bulk)
        self.bulk_orders_table.set_rows(self._numbered(children))

    # A delivery and one of ITS orders are different targets: with an order
    # highlighted, every button that acts on the whole delivery goes inactive
    # (only "Mark ... Delivered" applies), and with a delivery highlighted
    # the order button goes inactive again - so it's always clear which
    # buttons apply to what is selected.
    def _sync_light_buttons(self):
        has_delivery = self.selected_light is not None
        order_mode = self.selected_light_order is not None
        delivery_actions_on = has_delivery and not order_mode
        self.new_light_btn.setEnabled(not order_mode)
        self.light_status_btn.setEnabled(delivery_actions_on)
        self.light_complete_btn.setEnabled(delivery_actions_on)
        # "if a delivery was completed ... dont allow it to be assigned new
        # orders since it means it was completed so you either create a new
        # one or use one still active" - a delivered delivery can't take a
        # new order, whether it was marked delivered here or on mobile.
        is_delivered = has_delivery and self.selected_light.get("status") == "DELIVERED"
        self.assign_light_btn.setEnabled(delivery_actions_on and not is_delivered)
        self._set_highlight(self.assign_light_btn, delivery_actions_on and not is_delivered)
        self.light_order_delivered_btn.setEnabled(order_mode)

    def _sync_bulk_buttons(self):
        has_delivery = self.selected_bulk is not None
        order_mode = self.selected_bulk_order is not None
        delivery_actions_on = has_delivery and not order_mode
        self.new_bulk_btn.setEnabled(not order_mode)
        is_delivered = has_delivery and bool(self.selected_bulk.get("deliveredAt"))
        self.bulk_side_quest_btn.setEnabled(delivery_actions_on and not is_delivered)
        self.bulk_results_btn.setEnabled(delivery_actions_on)
        self.bulk_production_btn.setEnabled(delivery_actions_on)
        self.bulk_payment_btn.setEnabled(delivery_actions_on)
        self.bulk_delete_btn.setEnabled(delivery_actions_on)
        self._set_highlight(self.bulk_side_quest_btn, delivery_actions_on)
        self.bulk_order_delivered_btn.setEnabled(order_mode)

    @staticmethod
    def _set_highlight(button: QPushButton, on: bool):
        button.setProperty("highlight", on)
        button.style().unpolish(button)
        button.style().polish(button)

    # --- Loans -----------------------------------------------------------

    def _on_loan_selected(self, loan: dict):
        self.selected_loan = loan
        # Quick analysis right beside the selection: paid and what remains.
        self.loan_selection_label.setText(
            f"Selected: Loan #{loan.get('loanId') or loan.get('id')}   -   "
            f"Paid KES {money(loan.get('paidTotal'))}   Remainder KES {money(loan.get('remaining'))}"
        )
        self.loan_details_btn.setEnabled(True)
        self.edit_loan_btn.setEnabled(True)
        self.repayment_btn.setEnabled(True)
        self.pay_loan_btn.setEnabled(True)

    def _open_loan_details(self, loan: dict | None):
        if not loan:
            return
        self._on_loan_selected(loan)
        dialog = LoanDetailDialog(self.api_client, loan, on_changed=self._load_loans, parent=self)
        dialog.exec()
        self._load_loans()

    def _on_expenses_changed(self):
        # A paid or new expense moves the trip's totals and profit.
        self._load_summary()

    def _loan_fields(self):
        return [
            FieldSpec("amount", "Amount (KES)", type="float", required=True, maximum=100_000_000),
            FieldSpec("description", "Description"),
            FieldSpec("category", "Category", placeholder="e.g. Fuel advance"),
        ]

    def _open_create_loan(self):
        dialog = RecordFormDialog(
            "New Loan",
            self._loan_fields(),
            save_fn=lambda values: self.api_client.post(f"/trips/{self.trip_id}/finance/loans", json=values, actor=True),
            parent=self,
        )
        if dialog.exec():
            self._load_loans()

    def _open_edit_loan(self):
        if not self.selected_loan:
            return
        loan_id = self.selected_loan.get("loanId") or self.selected_loan.get("id")
        fields = self._loan_fields() + [
            FieldSpec(
                "status", "Status", type="combo",
                options=[("Active", "ACTIVE"), ("Paid", "PAID"), ("Written off", "WRITTEN_OFF")],
            )
        ]
        dialog = RecordFormDialog(
            f"Edit Loan #{loan_id}",
            fields,
            save_fn=lambda values: self.api_client.patch(f"/loans/{loan_id}", json=values, actor=True),
            initial_values=self.selected_loan,
            parent=self,
        )
        if dialog.exec():
            self._load_loans()

    def _open_repayment(self):
        if not self.selected_loan:
            return
        loan_id = self.selected_loan.get("loanId") or self.selected_loan.get("id")
        fields = [
            FieldSpec("amountPaid", "Amount paid (KES)", type="float", required=True, maximum=100_000_000),
            FieldSpec("description", "Description"),
        ]
        dialog = RecordFormDialog(
            f"Record Repayment - Loan #{loan_id}",
            fields,
            save_fn=lambda values: self.api_client.post(f"/loans/{loan_id}/repayments", json=values),
            parent=self,
        )
        if dialog.exec():
            self._load_loans()

    def _open_pay_loan(self):
        if not self.selected_loan:
            return
        loan_id = self.selected_loan.get("loanId") or self.selected_loan.get("id")
        dialog = PayoutDialog(
            self.api_client,
            f"Pay Loan #{loan_id} - Send Money",
            purpose=f"Loan #{loan_id}",
            recipient_target="LOAN",
            recipient_row_id=loan_id,
            send_fn=lambda amount, phone: self.api_client.post(
                f"/loans/{loan_id}/repayments/pay", json={"amount": amount, "phoneNumber": phone}, actor=True, timeout=85
            ),
            prefilled_amount=self.selected_loan.get("remaining") or self.selected_loan.get("amount"),
            parent=self,
        )
        dialog.exec()
        # Refresh either way - a payout that finished after closing still counts.
        self._load_loans()

    # --- Capital ---------------------------------------------------------

    def _load_capital(self):
        self._update_cash_split_label()
        self._run(
            lambda: self.api_client.get(f"/trips/{self.trip_id}/finance/capital"),
            lambda rows: self.capital_table.set_rows(rows or []),
            self.capital_table.set_error,
        )

    def _update_cash_split_label(self):
        hard = self.trip.get("hardCash") or 0
        soft = self.trip.get("softCash") or 0
        self.cash_split_label.setText(f"Hard cash: KES {hard}   Soft cash: KES {soft}")

    def _open_edit_cash_split(self):
        fields = [
            FieldSpec("hardCash", "Hard cash (KES)", type="float", maximum=100_000_000),
            FieldSpec("softCash", "Soft cash (KES)", type="float", maximum=100_000_000),
        ]

        def save(values: dict):
            return self.api_client.put(f"/trips/{self.trip_id}/finance/cash-split", json=values)

        dialog = RecordFormDialog(
            "Edit Cash Split", fields, save_fn=save, initial_values=self.trip, parent=self
        )
        if dialog.exec():
            self._run(
                lambda: self.api_client.get(f"/trips/{self.trip_id}"),
                self._on_trip_refreshed,
                lambda _msg: None,
            )

    def _on_trip_refreshed(self, trip: dict):
        self.trip = trip or self.trip
        self._update_cash_split_label()
        self._load_summary()

    def _recalculate_total_payment(self):
        self._run(
            lambda: self.api_client.post(f"/trips/{self.trip_id}/total-payment/recalculate"),
            lambda _result: self._load_summary(),
            lambda msg: QMessageBox.critical(self, "Couldn't recalculate", msg),
        )

    def _capital_fields(self):
        return [
            FieldSpec("amount", "Amount (KES)", type="float", required=True, maximum=100_000_000),
            FieldSpec("description", "Description"),
            FieldSpec("category", "Category", placeholder="e.g. Owner contribution"),
        ]

    def _on_capital_selected(self, row: dict):
        self.selected_capital = row
        self.edit_capital_btn.setEnabled(True)

    def _open_edit_capital(self):
        if not self.selected_capital:
            return
        capital_id = self.selected_capital.get("capitalId")
        dialog = RecordFormDialog(
            f"Edit Capital Entry #{capital_id}",
            self._capital_fields(),
            save_fn=lambda values: self.api_client.patch(f"/finance/capital/{capital_id}", json=values, actor=True),
            initial_values=self.selected_capital,
            parent=self,
        )
        if dialog.exec():
            self._load_capital()
            self._load_summary()

    def _open_create_capital(self):
        fields = [
            FieldSpec("amount", "Amount (KES)", type="float", required=True, maximum=100_000_000),
            FieldSpec("description", "Description"),
            FieldSpec("category", "Category", placeholder="e.g. Owner contribution"),
        ]
        dialog = RecordFormDialog(
            "New Capital Entry",
            fields,
            save_fn=lambda values: self.api_client.post(f"/trips/{self.trip_id}/finance/capital", json=values, actor=True),
            parent=self,
        )
        if dialog.exec():
            self._load_capital()
            self._load_summary()

    # --- Light deliveries --------------------------------------------------

    def _on_light_selected(self, delivery: dict):
        self.selected_light = delivery
        self.light_selection_label.setText(
            f"Selected: Delivery #{delivery.get('lightDeliveryId') or delivery.get('id')} - {delivery.get('status')}"
        )
        # Picking a delivery (again) returns to delivery-level actions.
        self.selected_light_order = None
        self.light_order_label.setText("Click an order above to mark just that one delivered.")
        # "when you click a delivery ... the assign pending order [is]
        # highlighted" - the obvious next step lights up once one is picked.
        self._sync_light_buttons()
        self._refresh_light_orders()

    def _open_light_status(self):
        if not self.selected_light:
            return
        delivery_id = self.selected_light.get("lightDeliveryId") or self.selected_light.get("id")
        fields = [FieldSpec("status", "Status", type="combo", required=True, options=LIGHT_STATUS_OPTIONS)]
        dialog = RecordFormDialog(
            f"Update Status - Delivery #{delivery_id}",
            fields,
            save_fn=lambda values: self.api_client.patch(f"/deliveries/light/{delivery_id}/status", json=values),
            initial_values=self.selected_light,
            parent=self,
        )
        if dialog.exec():
            self._load_deliveries()

    def _vehicle_options(self):
        return [("— none —", None)] + [
            (v.get("uniqueIdentifier") or f"Vehicle #{v.get('vehicleId')}", v.get("vehicleId"))
            for v in self.vehicles
        ]

    def _open_create_light_delivery(self):
        """"in desktop in both deliveries allow one to create one" - opens a
        light delivery on this trip; pending orders are then assigned
        under it (select it, then Assign Pending Order)."""
        def open_form(areas):
            # "i dont seem to have the destination written in it and the
            # logic is that we make decisions based on areas of delivery
            # so we need this so we know if we are adding it to a bunch
            # or creating another delivery" - this form used to offer a
            # "- none -" option and never required a real one, so a light
            # delivery (unlike bulk, which already requires "Delivered
            # to") could be created with no area at all - confirmed live
            # against the database: three real orders, including pending
            # ones, ended up with a null Delivery_Area_ID this way.
            area_options = [
                (f"{n}. {a.get('city')} - {a.get('areaName')}", a.get("deliveryAreaId"))
                for n, a in enumerate(sorted(areas or [], key=lambda a: a.get("deliveryAreaId") or 0), start=1)
            ]
            fields = [
                FieldSpec("deliveryAreaId", "Heading to (area)", type="combo", required=True, options=area_options),
                FieldSpec("vehicleId", "Vehicle", type="combo", options=self._vehicle_options()),
                FieldSpec("estimatedDeliveryDate", "Delivery date", type="date"),
            ]
            dialog = RecordFormDialog(
                f"New Light Delivery - Trip #{self.trip_id}",
                fields,
                save_fn=lambda values: self.api_client.post(
                    "/deliveries/light", json={**values, "tripId": self.trip_id}, actor=True
                ),
                initial_values={"estimatedDeliveryDate": self.trip.get("endDate")},
                parent=self,
            )
            if dialog.exec():
                self._load_deliveries()

        self._run(
            lambda: self.api_client.get("/shipping/areas"),
            open_form,
            lambda message: QMessageBox.critical(self, "Couldn't load delivery areas", message),
        )

    def _open_create_bulk_delivery(self):
        fields = [FieldSpec("deliveredTo", "Delivered to", required=True, placeholder="Customer / destination")]
        dialog = RecordFormDialog(
            f"New Bulk Delivery - Trip #{self.trip_id}",
            fields,
            save_fn=lambda values: self.api_client.post(
                "/deliveries/bulk", json={**values, "tripId": self.trip_id}, actor=True
            ),
            parent=self,
        )
        if dialog.exec():
            self._load_deliveries()

    def _open_assign_pending(self, parent_light: dict | None = None, bulk: dict | None = None):
        """"when i click that order then from there i can get the assign
        pending order ... so that an order is linked to a specific trip not
        just the global" - picks a pending customer order not yet on any
        delivery and links it to the delivery that was selected: grouped
        under a light delivery, or as a "side quest" riding along with a
        bulk delivery that still has space/time. Either way the customer's
        Ordered Goods then shows the concrete date entered here instead of
        the generic 3-week/3-month estimate."""
        if bulk is not None:
            target_id = bulk.get("bulkDeliveryId") or bulk.get("id")
            title = f"Side Quest - Bulk Delivery #{target_id}"
            hint_text = (
                "Light orders waiting for a delivery, numbered in the order they were placed. Purple rows "
                "missed an earlier delivery - reassign those first. Double-click one to send it along as a "
                "side quest with this bulk delivery."
            )
            default_vehicle, default_date = None, self.trip.get("endDate")
        else:
            target_id = parent_light.get("lightDeliveryId") or parent_light.get("id")
            title = f"Assign Pending Order - Delivery #{target_id}"
            hint_text = (
                "Orders waiting for a delivery, numbered in the order they were placed. Purple rows missed an "
                "earlier delivery - reassign those first. Double-click one heading the same direction as this "
                "delivery."
            )
            default_vehicle = parent_light.get("vehicleId")
            default_date = parent_light.get("estimatedDeliveryDate") or self.trip.get("endDate")

        dialog = QDialog(self)
        dialog.setWindowTitle(title)
        dialog.resize(640, 420)
        dialog_layout = QVBoxLayout(dialog)

        hint = QLabel(hint_text)
        hint.setProperty("role", "muted")
        hint.setWordWrap(True)
        dialog_layout.addWidget(hint)

        table = DataTable("Unassigned Orders", show_limit_selector=False)
        dialog_layout.addWidget(table)
        table.refresh_requested.connect(lambda _limit: load())

        def show_pool(rows):
            # The backend sends these oldest-order-first (by the orders'
            # own primary key), so 1..N is the order they were placed in.
            shaped = [
                {
                    "#": i,
                    "orderId": r.get("orderId"),
                    "customer": r.get("customer"),
                    "items": r.get("items"),
                    "area": f"{r.get('deliveryCity') or ''} - {r.get('areaName') or ''}".strip(" -"),
                    "total": r.get("total"),
                    "ordered": r.get("createdAt"),
                    "priority": "REASSIGN FIRST" if r.get("needsReassignment") else "",
                    "reason": r.get("undeliveredReason"),
                    "lightDeliveryId": r.get("lightDeliveryId"),
                }
                for i, r in enumerate(rows or [], start=1)
            ]
            table.set_rows(shaped, row_color=lambda row: PRIORITY_TINT if row.get("priority") else None)

        def load():
            table.set_loading()
            self._run(
                lambda: self.api_client.get("/deliveries/light/unassigned"),
                show_pool,
                table.set_error,
            )

        def on_selected(delivery: dict):
            light_delivery_id = delivery.get("lightDeliveryId") or delivery.get("id")
            fields = [
                FieldSpec("vehicleId", "Vehicle", type="combo", options=self._vehicle_options()),
                FieldSpec("estimatedDeliveryDate", "Delivery date", required=True, type="date"),
            ]
            initial_values = {"vehicleId": default_vehicle, "estimatedDeliveryDate": default_date}

            def save(values: dict):
                payload = {
                    "tripId": self.trip_id,
                    "vehicleId": values.get("vehicleId"),
                    "estimatedDeliveryDate": values.get("estimatedDeliveryDate"),
                }
                if bulk is not None:
                    payload["bulkDeliveryId"] = target_id
                else:
                    payload["parentLightDeliveryId"] = target_id
                return self.api_client.patch(
                    f"/deliveries/light/{light_delivery_id}/assign", json=payload, actor=True
                )

            assign_dialog = RecordFormDialog(
                f"Assign Order #{delivery.get('orderId')} - {title}",
                fields,
                save_fn=save,
                initial_values=initial_values,
                parent=dialog,
            )
            if assign_dialog.exec():
                dialog.accept()
                self._load_deliveries()

        table.row_selected.connect(on_selected)
        load()
        dialog.exec()

    def _on_light_order_selected(self, row: dict):
        self.selected_light_order = row
        self.light_order_label.setText(f"Selected: Order #{row.get('orderId')} - delivery buttons are off; click the delivery to use them again")
        self._sync_light_buttons()

    def _on_bulk_order_selected(self, row: dict):
        self.selected_bulk_order = row
        self.bulk_order_label.setText(f"Selected: Order #{row.get('orderId')} - delivery buttons are off; click the delivery to use them again")
        self._sync_bulk_buttons()

    def _mark_order_delivered(self, kind: str):
        """One order on its own arrived - close just that one out; it drops
        off the list, and whatever is still pending is dealt with when the
        delivery itself is finalised."""
        row = self.selected_light_order if kind == "light" else self.selected_bulk_order
        button = self.light_order_delivered_btn if kind == "light" else self.bulk_order_delivered_btn
        if not row:
            return
        what = "side quest" if kind == "bulk" else "order"
        answer = QMessageBox.question(
            self,
            "Mark as delivered?",
            f"Mark {what} #{row.get('orderId')} as delivered?\n\nIt will be removed from this list and the "
            "customer will see it as delivered.",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.Cancel,
            QMessageBox.StandardButton.Cancel,
        )
        if answer != QMessageBox.StandardButton.Yes:
            return
        light_id = row.get("lightDeliveryId")
        set_busy(button, True, "Saving")

        def done(_result):
            set_busy(button, False)
            if kind == "light":
                self.selected_light_order = None
                self.light_order_label.setText("Click an order above to mark just that one delivered.")
                self._sync_light_buttons()
            else:
                self.selected_bulk_order = None
                self.bulk_order_label.setText("Click a side quest above to mark just that one delivered.")
                self._sync_bulk_buttons()
            self._load_deliveries()

        def failed(message: str):
            set_busy(button, False)
            QMessageBox.critical(self, "Couldn't update the order", message)

        self._run(
            lambda: self.api_client.patch(f"/deliveries/light/{light_id}/status", json={"status": "DELIVERED"}),
            done,
            failed,
        )

    def _open_complete_light(self):
        head = self.selected_light
        if not head:
            return
        head_id = head.get("lightDeliveryId")
        # A container delivery (opened here, Total 0) has no order of its
        # own; an older standalone delivery IS one real order and is
        # counted alongside anything grouped under it.
        head_is_pending_order = bool(head.get("total")) and head.get("status") != "DELIVERED"
        items = ([head] if head_is_pending_order else []) + self._light_children(head)
        # "when one is finalizing the parent delivery and its status is
        # pending and that delivery is over then it goes back to the orders
        # that need to be assigned" - anything not already marked delivered
        # is treated as not delivered unless ticked here.
        self._open_settle_dialog(
            f"Complete Delivery #{head_id}", items, mark_delivered_id=head_id, default_checked=False,
        )

    def _open_complete_bulk_side_quests(self):
        bulk = self.selected_bulk
        if not bulk:
            return
        bulk_id = bulk.get("bulkDeliveryId")
        items = self._bulk_children(bulk)
        if not items:
            QMessageBox.information(self, "No side quests", "This bulk delivery has no side quests to settle.")
            return
        self._open_settle_dialog(f"Side Quest Results - Bulk Delivery #{bulk_id}", items)

    def _open_settle_dialog(
        self, title: str, items: list[dict], mark_delivered_id: int | None = None, default_checked: bool = True
    ):
        """"any product that was not delivered for one or more issues ...
        will be dropped for reassignment in another delivery" - tick what
        actually arrived; anything left unticked is cut loose from this
        delivery and goes back to the pending pool flagged as priority."""
        dialog = QDialog(self)
        dialog.setWindowTitle(title)
        dialog.setMinimumWidth(460)
        layout = QVBoxLayout(dialog)

        if not items:
            intro_text = "No pending orders are left on this delivery - it will just be marked delivered."
        elif default_checked:
            intro_text = (
                "Tick each order that was delivered. If all are ticked, that's it - no note needed. "
                "Unticked orders go back to the pending list, highlighted so they're reassigned first."
            )
        else:
            intro_text = (
                "These orders were never marked delivered, so they go back to the pending list, "
                "highlighted so they're reassigned first. Tick any that did arrive."
            )
        intro = QLabel(intro_text)
        intro.setWordWrap(True)
        intro.setProperty("role", "muted")
        layout.addWidget(intro)

        checks: list[tuple[QCheckBox, dict]] = []
        for row in items:
            label = (
                f"Order #{row.get('orderId')}  -  {row.get('deliveryCity') or ''} {row.get('areaName') or ''}"
                f"  -  KES {row.get('total') or 0}"
            )
            box = QCheckBox(label)
            box.setChecked(default_checked)
            layout.addWidget(box)
            checks.append((box, row))

        reason = QLineEdit()
        reason.setPlaceholderText("Why weren't some delivered? (customer away, no space, damaged...)")
        layout.addWidget(reason)

        # The note only appears once at least one order is left unticked;
        # everything ticked means everything arrived - nothing to explain.
        def refresh_reason():
            reason.setVisible(any(not box.isChecked() for box, _row in checks))

        for box, _row in checks:
            box.toggled.connect(lambda _checked: refresh_reason())
        refresh_reason()

        error = QLabel("")
        error.setProperty("role", "error")
        error.setWordWrap(True)
        layout.addWidget(error)

        buttons = QHBoxLayout()
        buttons.addStretch()
        cancel = QPushButton("Cancel")
        cancel.clicked.connect(dialog.reject)
        buttons.addWidget(cancel)
        confirm = QPushButton("Confirm")
        confirm.setProperty("role", "primary")
        buttons.addWidget(confirm)
        layout.addLayout(buttons)

        def on_confirm():
            delivered = [r.get("lightDeliveryId") for box, r in checks if box.isChecked()]
            undelivered = [r.get("lightDeliveryId") for box, r in checks if not box.isChecked()]
            if undelivered and not reason.text().strip():
                error.setText("Add a short reason for the orders that weren't delivered.")
                return
            error.setText("")
            if mark_delivered_id is not None and mark_delivered_id not in delivered and mark_delivered_id not in undelivered:
                delivered.append(mark_delivered_id)
            set_busy(confirm, True, "Saving")
            cancel.setEnabled(False)

            def work():
                return self.api_client.post(
                    "/deliveries/light/settle",
                    json={"deliveredIds": delivered, "undeliveredIds": undelivered, "reason": reason.text().strip() or None},
                    actor=True,
                )

            def done(_result):
                set_busy(confirm, False)
                dialog.accept()
                self._load_deliveries()

            def failed(message: str):
                set_busy(confirm, False)
                cancel.setEnabled(True)
                error.setText(message)

            self._run(work, done, failed)

        confirm.clicked.connect(on_confirm)
        dialog.exec()

    # --- Bulk deliveries -----------------------------------------------

    def _on_bulk_selected(self, order: dict):
        self.selected_bulk = order
        bulk_id = order.get("bulkDeliveryId") or order.get("id")
        side_quests = sum(1 for row in self.light_rows if row.get("bulkDeliveryId") == bulk_id)
        self.bulk_selection_label.setText(
            f"Selected: Order #{bulk_id}" + (f" - {side_quests} side quest(s)" if side_quests else "")
        )
        self.selected_bulk_order = None
        self.bulk_order_label.setText("Click a side quest above to mark just that one delivered.")
        self._sync_bulk_buttons()
        self._refresh_bulk_orders()

    def _open_bulk_production(self):
        if not self.selected_bulk:
            return
        bulk_id = self.selected_bulk.get("bulkDeliveryId") or self.selected_bulk.get("id")

        dialog = QDialog(self)
        dialog.setWindowTitle(f"Production Description - Bulk Order #{bulk_id}")
        dialog.resize(560, 420)
        dialog_layout = QVBoxLayout(dialog)

        table = DataTable("Production Description Entries", show_limit_selector=False)
        table.hidden_columns = {"hasPhoto", "bulkTripProductionDescriptionId"}
        table.refresh_requested.connect(lambda _limit: load())
        dialog_layout.addWidget(table)
        selected_entry: dict = {}

        buttons_row = QHBoxLayout()
        add_btn = QPushButton("Add Entry")
        add_btn.setProperty("role", "primary")
        buttons_row.addWidget(add_btn)
        # "the ai will pick a picture ... and it will scan that picture
        # for data and fill them" - opens the same Add Entry form,
        # pre-filled from the scan, so the admin still reviews/edits
        # before saving rather than the AI ever writing directly.
        scan_btn = QPushButton("Scan Image (AI)")
        scan_btn.clicked.connect(lambda: scan_image())
        buttons_row.addWidget(scan_btn)
        # "add a photo instead of only scan with AI since an image will
        # still do fine" - an entry can just carry the photo; the form's
        # Photo field is enough on its own. This opens the one already saved.
        view_photo_btn = QPushButton("View Photo")
        view_photo_btn.setEnabled(False)
        view_photo_btn.clicked.connect(lambda: view_photo())
        buttons_row.addWidget(view_photo_btn)
        # "provide a place to allow one to edit since one might have missed
        # on some issues" - a mistyped total or wrong measurement no longer
        # has to live with the order forever.
        edit_entry_btn = QPushButton("Edit Entry")
        edit_entry_btn.setEnabled(False)
        edit_entry_btn.clicked.connect(lambda: open_add(initial_values=selected_entry, entry_id=selected_entry.get("bulkTripProductionDescriptionId")))
        buttons_row.addWidget(edit_entry_btn)
        delete_entry_btn = QPushButton("Delete Entry")
        delete_entry_btn.setProperty("role", "danger")
        delete_entry_btn.setEnabled(False)
        delete_entry_btn.clicked.connect(lambda: delete_entry())
        buttons_row.addWidget(delete_entry_btn)
        buttons_row.addStretch()
        dialog_layout.addLayout(buttons_row)

        def load():
            table.set_loading()
            self._run(
                lambda: self.api_client.get(f"/deliveries/bulk/{bulk_id}/production"),
                show_entries,
                table.set_error,
            )

        def show_entries(rows):
            shaped = [{**r, "photo": CellNote("Photo", "camera") if r.get("hasPhoto") else ""} for r in rows or []]
            table.set_rows(shaped)
            selected_entry.clear()
            view_photo_btn.setEnabled(False)
            edit_entry_btn.setEnabled(False)
            delete_entry_btn.setEnabled(False)

        def on_entry_selected(entry: dict):
            selected_entry.clear()
            selected_entry.update(entry)
            view_photo_btn.setEnabled(bool(entry.get("hasPhoto")))
            edit_entry_btn.setEnabled(True)
            delete_entry_btn.setEnabled(True)

        table.row_selected.connect(on_entry_selected)

        def delete_entry():
            entry_id = selected_entry.get("bulkTripProductionDescriptionId")
            if not entry_id:
                return
            confirm = QMessageBox.question(
                dialog, "Delete entry?", "Remove this production description entry? This can't be undone."
            )
            if confirm != QMessageBox.StandardButton.Yes:
                return
            self._run(
                lambda: self.api_client.delete(f"/deliveries/bulk/production/{entry_id}", actor=True),
                lambda _r: load(),
                lambda message: QMessageBox.critical(dialog, "Couldn't delete", message),
            )

        def view_photo():
            entry_id = selected_entry.get("bulkTripProductionDescriptionId")
            if not entry_id:
                return

            def show(result):
                from app.ui.widgets.image_picker import data_url_to_image
                from PySide6.QtGui import QPixmap
                from PySide6.QtCore import Qt as _Qt

                image = data_url_to_image((result or {}).get("photo"))
                viewer = QDialog(dialog)
                viewer.setWindowTitle("Entry photo")
                box = QVBoxLayout(viewer)
                label = QLabel()
                label.setPixmap(
                    QPixmap.fromImage(image).scaled(900, 700, _Qt.AspectRatioMode.KeepAspectRatio, _Qt.TransformationMode.SmoothTransformation)
                )
                box.addWidget(label)
                viewer.exec()

            self._run(
                lambda: self.api_client.get(f"/deliveries/bulk/production/{entry_id}/photo"),
                show,
                lambda message: QMessageBox.critical(dialog, "Couldn't load the photo", message),
            )

        def open_add(initial_values: dict | None = None, entry_id: int | None = None):
            fields = [
                FieldSpec("productType", "Product type", placeholder="e.g. Eucalyptus"),
                FieldSpec(
                    "productDescription", "Product description", type="textarea",
                    placeholder="e.g. 6*6*4 5*6*9 ..."
                ),
                FieldSpec("productMeasurement", "Measurement", placeholder="e.g. board feet"),
                FieldSpec("totalBodyFeet", "Total body feet", type="float", maximum=1_000_000),
                FieldSpec("productTotal", "Product total", type="float", maximum=1_000_000),
                FieldSpec("photo", "Photo", type="image"),
            ]
            save_fn = (
                (lambda values: self.api_client.patch(f"/deliveries/bulk/production/{entry_id}", json=values))
                if entry_id
                else (lambda values: self.api_client.post(f"/deliveries/bulk/{bulk_id}/production", json=values))
            )
            add_dialog = RecordFormDialog(
                "Edit Production Description Entry" if entry_id else "New Production Description Entry",
                fields,
                save_fn=save_fn,
                initial_values=initial_values,
                parent=dialog,
            )
            if add_dialog.exec():
                load()

        def scan_image():
            file_path, _filter = QFileDialog.getOpenFileName(
                dialog, "Select a photo of the handwritten note", "", "Images (*.png *.jpg *.jpeg *.webp)"
            )
            if not file_path:
                return

            mime_type, _encoding = mimetypes.guess_type(file_path)
            if not mime_type or not mime_type.startswith("image/"):
                mime_type = "image/jpeg"
            with open(file_path, "rb") as f:
                image_bytes = f.read()
            data_url = f"data:{mime_type};base64,{base64.b64encode(image_bytes).decode('ascii')}"

            scan_btn.setEnabled(False)
            scan_btn.setText("Scanning...")

            def on_scanned(result: dict):
                scan_btn.setEnabled(True)
                scan_btn.setText("Scan Image (AI)")
                # Keep the scanned photo with the entry too.
                open_add(initial_values={**(result or {}), "photo": data_url})

            def on_error(message: str):
                scan_btn.setEnabled(True)
                scan_btn.setText("Scan Image (AI)")
                QMessageBox.critical(dialog, "Couldn't scan image", message)

            self._run(
                lambda: self.api_client.post(
                    f"/deliveries/bulk/{bulk_id}/production/scan", json={"imageDataUrl": data_url}, timeout=50
                ),
                on_scanned,
                on_error,
            )

        # Qt's clicked signal passes a bool ("checked") as the first arg -
        # explicit no-arg calls here so that never lands in initial_values.
        add_btn.clicked.connect(lambda: open_add())
        load()
        dialog.exec()

    def _open_bulk_payment(self):
        if not self.selected_bulk:
            return
        bulk_id = self.selected_bulk.get("bulkDeliveryId") or self.selected_bulk.get("id")

        def open_with(current: dict | None):
            current = current or self.selected_bulk
            delivered = current.get("deliveredAt") is not None
            fields = [
                FieldSpec("hardCashAmount", "Hard cash", type="float", maximum=100_000_000),
                FieldSpec("softCashAmount", "Soft cash", type="float", maximum=100_000_000),
            ]
            dialog = RecordFormDialog(
                f"Edit Payment - Bulk Order #{bulk_id}" if delivered else f"Record Payment - Bulk Order #{bulk_id}",
                fields,
                # productType left out on purpose - this only ever touches
                # the cash split, never adds a second production-description
                # row (see DeliveryService.finalizeBulkDelivery).
                save_fn=lambda values: self.api_client.patch(
                    f"/deliveries/bulk/{bulk_id}/finalize", json=values, actor=True
                ),
                initial_values={
                    "hardCashAmount": current.get("hardCashAmount") or 0,
                    "softCashAmount": current.get("softCashAmount") or 0,
                },
                parent=self,
            )
            if dialog.exec():
                # The trip's profit is computed from this figure live, but
                # the on-screen summary needs a fresh fetch to show it.
                self._load_deliveries()
                self._load_summary()

        self._run(lambda: self.api_client.get(f"/deliveries/bulk/{bulk_id}"), open_with, lambda _msg: open_with(None))

    def _delete_bulk_order(self):
        if not self.selected_bulk:
            return
        bulk_id = self.selected_bulk.get("bulkDeliveryId") or self.selected_bulk.get("id")
        confirm = QMessageBox.question(
            self, "Delete order?", f"Delete bulk order #{bulk_id}? \"if it fails its just deleted\" - this can't be undone."
        )
        if confirm != QMessageBox.StandardButton.Yes:
            return

        def on_done(_result):
            self.selected_bulk = None
            self.bulk_delete_btn.setEnabled(False)
            self.bulk_production_btn.setEnabled(False)
            self._load_deliveries()

        self._run(
            lambda: self.api_client.delete(f"/deliveries/bulk/{bulk_id}", actor=True),
            on_done,
            lambda msg: QMessageBox.critical(self, "Couldn't delete order", msg),
        )

    # --- Trees -----------------------------------------------------------

    def _load_trees(self):
        self._run(
            lambda: self.api_client.get("/trees", params={"tripId": self.trip_id}),
            lambda rows: self.trees_table.set_rows(rows or []),
            self.trees_table.set_error,
        )

    def _load_farmers_and_remainders(self):
        self._run(lambda: self.api_client.get("/farmers"), self._on_farmers_loaded, lambda _msg: None)
        self._run(lambda: self.api_client.get("/remainder-trees"), self._on_remainders_loaded, lambda _msg: None)

    def _on_farmers_loaded(self, farmers):
        self.farmers = farmers or []

    def _on_remainders_loaded(self, remainders):
        self.remainder_trees = remainders or []

    def _tree_fields(self, editing_trees_id: int | None = None):
        farmer_options = [("(none)", None)] + [(f.get("name"), f.get("farmerId")) for f in self.farmers]
        # "if a remainder tree is claimed then dont let it to be claimed by
        # another tree creation" - exclude anything already carried
        # forward by some OTHER tree; a tree being edited keeps seeing its
        # own claimed remainder as a valid option (it already owns it).
        available_remainders = [
            r for r in self.remainder_trees
            if r.get("claimedByTreesId") is None or r.get("claimedByTreesId") == editing_trees_id
        ]
        remainder_options = [("(none - fresh registration)", None)] + [
            (f"#{r.get('remainderTreesId')} - {r.get('noTrees')} {r.get('typeTrees') or ''} (from trip {r.get('previousTripId')})", r.get("remainderTreesId"))
            for r in available_remainders
        ]
        return [
            FieldSpec("houseHold", "Household"),
            FieldSpec("coordinates", "Coordinates", placeholder="lat,long"),
            FieldSpec("noTrees", "Number of trees", type="int", maximum=1_000_000),
            FieldSpec("typeTrees", "Tree type", placeholder="e.g. Eucalyptus"),
            FieldSpec("active", "Active", type="checkbox"),
            FieldSpec("farmerId", "Farmer", type="combo", options=farmer_options),
            FieldSpec(
                "newFarmerName", "New farmer's name", placeholder="Leave blank to use the Farmer picker above"
            ),
            FieldSpec("newFarmerPhone", "New farmer's phone"),
            FieldSpec(
                "remainderTreesId", "Carried forward from remainder", type="combo", options=remainder_options
            ),
        ]

    def _on_tree_selected(self, tree: dict):
        self.selected_tree = tree
        self.tree_selection_label.setText(
            f"Selected: #{tree.get('treesId')} - {tree.get('houseHold') or 'no household'} ({tree.get('noTrees')} {tree.get('typeTrees') or ''})"
        )
        self.edit_tree_btn.setEnabled(True)
        self.record_remainder_btn.setEnabled(True)

    def _open_record_remainder(self):
        if not self.selected_tree:
            return
        fields = [
            FieldSpec(
                "noTrees", "Number of remainder trees", type="int", required=True, maximum=1_000_000
            ),
            FieldSpec("typeTrees", "Tree type", placeholder="e.g. Eucalyptus"),
            FieldSpec("totalBodyFeet", "Total body feet", type="float", maximum=1_000_000),
        ]

        def save(values: dict):
            values["previousTripId"] = self.trip_id
            values["originTreesId"] = self.selected_tree.get("treesId")
            return self.api_client.post("/remainder-trees", json=values)

        initial = {
            "noTrees": self.selected_tree.get("noTrees"),
            "typeTrees": self.selected_tree.get("typeTrees"),
        }
        dialog = RecordFormDialog(
            f"Record Remainder - from tree #{self.selected_tree.get('treesId')} "
            f"({self.selected_tree.get('houseHold') or 'no household'})",
            fields,
            save_fn=save,
            initial_values=initial,
            parent=self,
        )
        if dialog.exec():
            self._load_farmers_and_remainders()
            QMessageBox.information(
                self, "Remainder recorded",
                "A later trip can now carry this forward via its own Tree registration."
            )

    def _resolve_tree_save_values(self, values: dict) -> dict:
        """"i cannot see the option to create a new farmer if they do not
        exist" - if the admin filled in a new farmer's name instead of
        picking one, register that farmer first and use its id; either
        way, newFarmerName/newFarmerPhone never get sent to /trees."""
        new_farmer_name = (values.pop("newFarmerName", None) or "").strip()
        new_farmer_phone = values.pop("newFarmerPhone", None)
        if new_farmer_name:
            farmer = self.api_client.post(
                "/farmers",
                json={"name": new_farmer_name, "phoneNo": new_farmer_phone, "bankDetailsMarked": None, "balance": 0},
            )
            values["farmerId"] = farmer.get("farmerId")
        return values

    def _open_create_tree(self):
        def save(values: dict):
            values = self._resolve_tree_save_values(values)
            values["tripId"] = self.trip_id
            return self.api_client.post("/trees", json=values)

        dialog = RecordFormDialog(
            "New Tree Registration", self._tree_fields(), save_fn=save, initial_values={"active": True}, parent=self
        )
        if dialog.exec():
            self._load_trees()
            self._load_farmers_and_remainders()

    # --- Machinery / Vehicles ---------------------------------------------
    # "machine should be selected from the machine table" / "select the
    # vehicle one used instead of using the vehicle id" - this registry
    # isn't trip-scoped (MACHINE/VEHICLE have no Trip_ID), so the list
    # loaded here is every machine/vehicle in the system, not just this
    # trip's - the tab just lives here because that's where an admin is
    # about to record a machine/transport expense and needs to add one.

    def _load_machinery(self):
        self._run(
            lambda: self.api_client.get("/machines"),
            lambda rows: self.machinery_table.set_rows(rows or []),
            self.machinery_table.set_error,
        )

    def _machine_fields(self):
        return [
            FieldSpec("name", "Name", required=True),
            FieldSpec("serialNo", "Serial number"),
            FieldSpec("description", "Description", type="textarea"),
        ]

    def _on_machine_selected(self, row: dict):
        self.selected_machine = row
        self.machine_selection_label.setText(f"Selected: {row.get('name')}")
        self.edit_machine_btn.setEnabled(True)

    def _open_edit_machine(self):
        if not self.selected_machine:
            return
        machine_id = self.selected_machine.get("machineId")
        dialog = RecordFormDialog(
            f"Edit Machine - {self.selected_machine.get('name')}",
            self._machine_fields(),
            save_fn=lambda values: self.api_client.patch(f"/machines/{machine_id}", json=values),
            initial_values=self.selected_machine,
            parent=self,
        )
        if dialog.exec():
            self._load_machinery()

    def _open_create_machine(self):
        fields = [
            FieldSpec("name", "Name", required=True),
            FieldSpec("serialNo", "Serial number"),
            FieldSpec("description", "Description", type="textarea"),
        ]
        dialog = RecordFormDialog(
            "New Machine", fields, save_fn=lambda values: self.api_client.post("/machines", json=values), parent=self
        )
        if dialog.exec():
            self._load_machinery()

    def _load_vehicles(self):
        def on_done(rows):
            self.vehicles = rows or []
            self.vehicles_table.set_rows(self.vehicles)

        self._run(lambda: self.api_client.get("/vehicles"), on_done, self.vehicles_table.set_error)

    def _vehicle_fields(self):
        return [
            FieldSpec("uniqueIdentifier", "Number plate / unique ID", required=True),
            FieldSpec("driver", "Driver"),
            FieldSpec("phoneNumber", "Phone number"),
            FieldSpec("description", "Description", type="textarea"),
        ]

    def _on_vehicle_selected(self, row: dict):
        self.selected_vehicle = row
        self.vehicle_selection_label.setText(f"Selected: {row.get('uniqueIdentifier')}")
        self.edit_vehicle_btn.setEnabled(True)

    def _open_edit_vehicle(self):
        if not self.selected_vehicle:
            return
        vehicle_id = self.selected_vehicle.get("vehicleId")
        dialog = RecordFormDialog(
            f"Edit Vehicle - {self.selected_vehicle.get('uniqueIdentifier')}",
            self._vehicle_fields(),
            save_fn=lambda values: self.api_client.patch(f"/vehicles/{vehicle_id}", json=values),
            initial_values=self.selected_vehicle,
            parent=self,
        )
        if dialog.exec():
            self._load_vehicles()

    def _open_create_vehicle(self):
        fields = [
            FieldSpec("uniqueIdentifier", "Number plate / unique ID", required=True),
            FieldSpec("driver", "Driver"),
            FieldSpec("phoneNumber", "Phone number"),
            FieldSpec("description", "Description", type="textarea"),
        ]
        dialog = RecordFormDialog(
            "New Vehicle", fields, save_fn=lambda values: self.api_client.post("/vehicles", json=values), parent=self
        )
        if dialog.exec():
            self._load_vehicles()

    def _open_edit_tree(self):
        if not self.selected_tree:
            return
        trees_id = self.selected_tree.get("treesId")

        def save(values: dict):
            values = self._resolve_tree_save_values(values)
            values["tripId"] = self.trip_id
            return self.api_client.patch(f"/trees/{trees_id}", json=values)

        dialog = RecordFormDialog(
            f"Edit Tree #{trees_id}",
            self._tree_fields(editing_trees_id=trees_id),
            save_fn=save,
            initial_values=self.selected_tree,
            parent=self,
        )
        if dialog.exec():
            self._load_trees()
            self._load_farmers_and_remainders()
