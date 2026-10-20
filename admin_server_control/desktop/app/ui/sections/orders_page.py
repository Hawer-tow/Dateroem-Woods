"""Insights -> Orders: the pending pool gathered by customer.

"some of these orders may be from the same person so how do we collect them
in a single group so that an admin can gauge the goods and then check if
there is a bulk order or light or combined in order to make a decision" -
one row per customer per delivery area, each carrying the goods gauge (how
many orders, how much timber, is it light / bulk / a mix). Pick a group,
choose Light or Bulk, pick a trip, then a delivery (an existing one, or a
new one) and tick which of the group's orders go on it - anything left
unticked simply stays pending.
"""
from datetime import date

from PySide6.QtCore import Qt, QThreadPool
from PySide6.QtGui import QColor
from PySide6.QtWidgets import (
    QCheckBox,
    QComboBox,
    QDialog,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QPushButton,
    QSplitter,
    QStackedWidget,
    QVBoxLayout,
    QWidget,
)

from app.core.worker import Worker
from app.ui.sections.trip_detail_dialog import PRIORITY_TINT
from app.ui.widgets.busy import set_busy
from app.ui.widgets.data_table import DataTable
from app.ui.widgets.date_picker import DatePickerField
from app.ui.widgets.payout_dialog import PayoutDialog

# Orders already on a delivery (a customer's assigned batch) - a calm green so
# they read as "settled" next to the new, still-unassigned ones below them.
ASSIGNED_TINT = QColor("#2e7d32")
ASSIGNED_TINT.setAlpha(80)

KIND_HINTS = {
    "LIGHT": "Every order is small items - this group suits a light delivery.",
    "BULK": "Every order holds a large item (over 500 body feet per unit) - this group suits a bulk delivery.",
}


def _fmt_feet(value) -> str:
    try:
        return f"{float(value):,.0f}"
    except (TypeError, ValueError):
        return "-"


class OrdersPage(QWidget):
    def __init__(self, api_client, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self._groups: list[dict] = []
        self.selected_group: dict | None = None
        self._active_workers = []

        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)

        hint = QLabel(
            "Pending orders gathered per customer and delivery area, oldest first. Click a group to gauge the goods, "
            "then send it to a light or bulk delivery."
        )
        hint.setProperty("role", "muted")
        hint.setWordWrap(True)
        layout.addWidget(hint)

        # A draggable split between the customer list and the selected group's
        # detail: the detail (gauge, orders, actions) used to get a small
        # fixed-height strip under a big list. Now the two share the space,
        # and the divider can be dragged to give either one more.
        self.splitter = QSplitter(Qt.Orientation.Vertical)
        self.splitter.setChildrenCollapsible(False)
        self.splitter.setHandleWidth(8)
        layout.addWidget(self.splitter, 1)

        self.groups_table = DataTable("Pending Orders by Customer", show_limit_selector=False, searchable=True, search_placeholder="Search by customer, phone or area")
        self.groups_table.hidden_columns = {"idx"}
        self.groups_table.setMinimumHeight(140)
        self.groups_table.refresh_requested.connect(lambda _limit: self.load())
        self.groups_table.row_clicked.connect(self._on_group_selected)
        self.groups_table.row_selected.connect(self._on_group_selected)
        self.splitter.addWidget(self.groups_table)

        detail = QWidget()
        detail_layout = QVBoxLayout(detail)
        detail_layout.setContentsMargins(0, 6, 0, 0)

        self.gauge_label = QLabel("Click a group above to see what's in it.")
        self.gauge_label.setWordWrap(True)
        self.gauge_label.setProperty("role", "muted")
        detail_layout.addWidget(self.gauge_label)

        self.orders_table = DataTable("Orders in this group", show_limit_selector=False)
        self.orders_table.hidden_columns = {"lightDeliveryId"}
        self.orders_table.setMinimumHeight(160)
        self.orders_table.refresh_requested.connect(lambda _limit: self.load())
        self.orders_table.row_clicked.connect(self._on_order_selected)
        self.orders_table.row_selected.connect(self._on_order_selected)
        detail_layout.addWidget(self.orders_table, 1)
        self.selected_order: dict | None = None

        actions = QHBoxLayout()
        actions.addStretch()
        # "if a new order comes from that user ... one can still add it in the
        # bunch if the staff deems worth it" - the customer's already
        # assigned batch, joined by the new order(s) listed below it.
        self.add_batch_btn = QPushButton("Add to Existing Batch")
        self.add_batch_btn.setEnabled(False)
        self.add_batch_btn.clicked.connect(self._open_add_to_batch)
        actions.addWidget(self.add_batch_btn)
        self.light_btn = QPushButton("Assign Group to Light Delivery")
        self.light_btn.setEnabled(False)
        self.light_btn.clicked.connect(lambda: self._open_wizard("light"))
        actions.addWidget(self.light_btn)
        self.bulk_btn = QPushButton("Assign Group to Bulk Delivery")
        self.bulk_btn.setEnabled(False)
        self.bulk_btn.clicked.connect(lambda: self._open_wizard("bulk"))
        actions.addWidget(self.bulk_btn)
        # "add the drop option ... for a particular order for a customer
        # you can add the drop and refund option so that the desktop admin
        # can also refund the cash" - acts on the single order row
        # selected in self.orders_table above, not the whole group; only
        # enabled for one that isn't already on a trip (dropOrder() itself
        # rejects those, same as the mobile app's own Assign Group screen).
        self.drop_btn = QPushButton("Drop Order")
        self.drop_btn.setProperty("role", "danger")
        self.drop_btn.setEnabled(False)
        self.drop_btn.clicked.connect(self._open_drop_order)
        actions.addWidget(self.drop_btn)
        detail_layout.addLayout(actions)

        self.splitter.addWidget(detail)
        self.splitter.setStretchFactor(0, 4)
        self.splitter.setStretchFactor(1, 5)
        self.splitter.setSizes([320, 400])

        self.load()

    def _run(self, fn, on_done, on_error):
        worker = Worker(fn)
        worker.signals.finished.connect(on_done)
        worker.signals.error.connect(on_error)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)

    @staticmethod
    def _highlight(button: QPushButton, on: bool):
        button.setProperty("highlight", on)
        button.style().unpolish(button)
        button.style().polish(button)

    def load(self):
        self.groups_table.set_loading()
        self._run(lambda: self.api_client.get("/deliveries/light/pending-groups"), self._show_groups, self.groups_table.set_error)

    def _show_groups(self, groups):
        self._groups = groups or []
        shaped = [
            {
                "#": i,
                "customer": g.get("customer") or "(unknown)",
                "area": g.get("area"),
                "new orders": g.get("orderCount"),
                "already assigned": g.get("assignedCount") or 0,
                "goods": g.get("itemsText"),
                "total (KES)": g.get("total"),
                "body feet": _fmt_feet(g.get("bodyFeet")),
                "type": g.get("kind"),
                "priority": "REASSIGN FIRST" if g.get("needsReassignment") else "",
                "idx": i - 1,
            }
            for i, g in enumerate(self._groups, start=1)
        ]
        self.groups_table.set_rows(shaped, row_color=lambda row: PRIORITY_TINT if row.get("priority") else None)

        # Keep the same customer selected across a reload where possible.
        if self.selected_group is not None:
            key = (self.selected_group.get("customerId"), self.selected_group.get("areaId"))
            match = next((g for g in self._groups if (g.get("customerId"), g.get("areaId")) == key), None)
            if match is not None:
                self._select(match)
                return
        self._clear_selection()

    def _clear_selection(self):
        self.selected_group = None
        self.selected_order = None
        self.gauge_label.setText("Click a group above to see what's in it.")
        self.orders_table.set_rows([])
        for button in (self.add_batch_btn, self.light_btn, self.bulk_btn, self.drop_btn):
            button.setEnabled(False)
            self._highlight(button, False)

    def _on_group_selected(self, row: dict):
        idx = row.get("idx")
        if idx is None or not (0 <= idx < len(self._groups)):
            return
        self._select(self._groups[idx])

    def _select(self, group: dict):
        self.selected_group = group
        self.selected_order = None
        self.drop_btn.setEnabled(False)
        kind = group.get("kind")
        orders = group.get("orders") or []
        large = group.get("largeOrders") or 0

        lines = [
            f"{group.get('customer') or '(unknown)'}  -  {group.get('area') or 'no area'}  -  {len(orders)} order(s)  -  "
            f"KES {group.get('total')}  -  {_fmt_feet(group.get('bodyFeet'))} body feet   =>   {kind}"
        ]
        if kind == "COMBINED":
            lines.append(
                f"Mixed: {large} of {len(orders)} orders hold large items. The large ones suit a bulk delivery, the small ones "
                "can ride along as side quests - or send the whole group light."
            )
        else:
            lines.append(KIND_HINTS.get(kind, ""))
        if group.get("unmeasured"):
            lines.append("Some products have no size set yet, so they're counted as small.")
        if group.get("needsReassignment"):
            lines.append("Includes orders that missed an earlier delivery - reassign these first.")
        self.gauge_label.setText("\n".join(line for line in lines if line))

        batches = group.get("assignedBatches") or []
        for batch in batches:
            word = "Bulk" if batch.get("kind") == "bulk" else "Light"
            lines.append(
                f"Already on a delivery: {word} delivery #{batch.get('targetId')} (trip #{batch.get('tripId')}, "
                f"{batch.get('date') or 'no date'}{', ' + batch['vehicle'] if batch.get('vehicle') else ''}) - "
                f"{len(batch.get('orders') or [])} order(s). The new orders listed under it can still be added to it."
            )
        self.gauge_label.setText("\n".join(line for line in lines if line))

        # "i want to see the delivery area for that order so i know where
        # to place it even before i add it to a bunch or light or bulk
        # delivery" - every order in this table belongs to the same
        # customer+area group (that's what a group is), so the group's
        # own area applies to every row here.
        area = group.get("area") or "no area"

        # The assigned batch first, then the customer's new unassigned orders
        # underneath it, numbered straight through.
        shaped = []
        for batch in batches:
            word = "Bulk" if batch.get("kind") == "bulk" else "Light"
            for o in batch.get("orders") or []:
                shaped.append({
                    "orderId": o.get("orderId"),
                    "area": area,
                    "goods": o.get("itemsText"),
                    "body feet": "",
                    "size": "",
                    "total (KES)": o.get("total"),
                    "M-Pesa receipt": o.get("mpesaReceipt") or "",
                    "ordered": "",
                    "status": f"ASSIGNED - {word} delivery #{batch.get('targetId')} - trip #{batch.get('tripId')} - {batch.get('date') or 'no date'}",
                    "priority": "",
                    "reason": "",
                    "lightDeliveryId": None,
                })
        for o in orders:
            shaped.append({
                "orderId": o.get("orderId"),
                "area": area,
                "goods": o.get("itemsText"),
                "body feet": _fmt_feet(o.get("bodyFeet")),
                "size": "LARGE" if o.get("large") else "small",
                "total (KES)": o.get("total"),
                # "why does the database not show the reference ... in the
                # desktop" - the payment's M-Pesa receipt (migration 035).
                "M-Pesa receipt": o.get("mpesaReceipt") or "",
                "ordered": o.get("orderedAt"),
                "status": "NEW - not assigned yet",
                "priority": "REASSIGN FIRST" if o.get("needsReassignment") else "",
                "reason": o.get("undeliveredReason"),
                "lightDeliveryId": o.get("lightDeliveryId"),
            })
        shaped = [{"#": i, **row} for i, row in enumerate(shaped, start=1)]

        def tint(row):
            if row.get("priority"):
                return PRIORITY_TINT
            return ASSIGNED_TINT if str(row.get("status", "")).startswith("ASSIGNED") else None

        self.orders_table.set_rows(shaped, row_color=tint)

        # An assigned batch exists: joining it is the natural next step.
        self.add_batch_btn.setEnabled(bool(batches))
        self._highlight(self.add_batch_btn, bool(batches))
        self.light_btn.setEnabled(True)
        self.bulk_btn.setEnabled(True)
        # Nudge toward the natural choice; a mixed group leaves both lit.
        self._highlight(self.light_btn, kind in ("LIGHT", "COMBINED"))
        self._highlight(self.bulk_btn, kind in ("BULK", "COMBINED"))

    def _open_add_to_batch(self):
        if not self.selected_group or not self.selected_group.get("assignedBatches"):
            return
        dialog = AddToBatchDialog(self.api_client, self.selected_group, parent=self)
        if dialog.exec():
            self.load()

    def _open_wizard(self, kind: str):
        if not self.selected_group:
            return
        wizard = AssignGroupWizard(self.api_client, self.selected_group, kind, parent=self)
        if wizard.exec():
            self.load()

    def _on_order_selected(self, row: dict):
        self.selected_order = row
        # Only a not-yet-assigned order can be dropped - one already on a
        # trip's delivery is rejected by dropOrder() itself either way.
        droppable = row.get("orderId") is not None and str(row.get("status", "")).startswith("NEW")
        self.drop_btn.setEnabled(droppable)

    def _open_drop_order(self):
        if not self.selected_order or self.selected_order.get("orderId") is None:
            return
        dialog = DropOrderDialog(self.api_client, self.selected_order, parent=self)
        if dialog.exec():
            self.load()


class AssignGroupWizard(QDialog):
    """Trip -> delivery (existing or new) -> tick the orders -> confirm."""

    def __init__(self, api_client, group: dict, kind: str, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.group = group
        self.kind = kind  # "light" | "bulk"
        self.trip: dict | None = None
        self.vehicles: list[dict] = []
        self._created_id: int | None = None  # a delivery already created by an earlier, failed confirm
        self._active_workers = []

        label = "Light" if kind == "light" else "Bulk"
        self.setWindowTitle(f"Assign {group.get('customer') or 'group'} to a {label} Delivery")
        self.resize(900, 640)

        layout = QVBoxLayout(self)
        self.step_label = QLabel("")
        self.step_label.setProperty("role", "title")
        layout.addWidget(self.step_label)

        self.pages = QStackedWidget()
        layout.addWidget(self.pages, 1)
        self.pages.addWidget(self._build_trip_page())
        self.pages.addWidget(self._build_delivery_page())

        self.error_label = QLabel("")
        self.error_label.setProperty("role", "error")
        self.error_label.setWordWrap(True)
        layout.addWidget(self.error_label)

        buttons = QHBoxLayout()
        buttons.addStretch()
        self.cancel_btn = QPushButton("Cancel")
        self.cancel_btn.clicked.connect(self.reject)
        buttons.addWidget(self.cancel_btn)
        self.back_btn = QPushButton("Back")
        self.back_btn.clicked.connect(lambda: self._go(0))
        buttons.addWidget(self.back_btn)
        self.next_btn = QPushButton("Next: Choose Delivery")
        self.next_btn.setProperty("role", "primary")
        self.next_btn.clicked.connect(self._on_next)
        buttons.addWidget(self.next_btn)
        self.confirm_btn = QPushButton("Confirm Assignment")
        self.confirm_btn.setProperty("role", "primary")
        self.confirm_btn.clicked.connect(self._confirm)
        buttons.addWidget(self.confirm_btn)
        layout.addLayout(buttons)

        self._go(0)
        self._run(lambda: self.api_client.get("/vehicles"), self._on_vehicles, lambda _m: None)
        self._load_trips()

    # -- plumbing ---------------------------------------------------------

    def _run(self, fn, on_done, on_error):
        worker = Worker(fn)
        worker.signals.finished.connect(on_done)
        worker.signals.error.connect(on_error)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)

    def _go(self, page: int):
        self.error_label.setText("")
        self.pages.setCurrentIndex(page)
        on_trip_page = page == 0
        self.back_btn.setVisible(not on_trip_page)
        self.next_btn.setVisible(on_trip_page)
        self.confirm_btn.setVisible(not on_trip_page)
        label = "light" if self.kind == "light" else "bulk"
        self.step_label.setText(
            f"Step 1 of 2 - choose the trip for this {label} delivery" if on_trip_page
            else f"Step 2 of 2 - Trip #{self.trip.get('tripId')}: pick or create the delivery, tick the orders"
        )

    # -- page 1: trip -----------------------------------------------------

    def _build_trip_page(self) -> QWidget:
        page = QWidget()
        box = QVBoxLayout(page)
        box.setContentsMargins(0, 0, 0, 0)
        hint = QLabel("Click the trip this group is travelling on.")
        hint.setProperty("role", "muted")
        box.addWidget(hint)
        self.trips_table = DataTable("Trips", show_limit_selector=False)
        self.trips_table.refresh_requested.connect(lambda _limit: self._load_trips())
        self.trips_table.row_clicked.connect(self._on_trip_picked)
        self.trips_table.row_selected.connect(self._on_trip_picked)
        box.addWidget(self.trips_table)
        self.trip_pick_label = QLabel("No trip selected yet.")
        self.trip_pick_label.setProperty("role", "muted")
        box.addWidget(self.trip_pick_label)
        return page

    def _load_trips(self):
        self.trips_table.set_loading()
        self._run(lambda: self.api_client.get("/trips"), lambda rows: self.trips_table.set_rows(rows or []), self.trips_table.set_error)

    def _on_trip_picked(self, row: dict):
        self.trip = row
        self.trip_pick_label.setText(f"Selected: Trip #{row.get('tripId')} - {row.get('productDescription') or 'no description'}")

    def _on_vehicles(self, rows):
        self.vehicles = rows or []
        self.vehicle_combo.clear()
        self.vehicle_combo.addItem("- none -", None)
        for v in self.vehicles:
            self.vehicle_combo.addItem(v.get("uniqueIdentifier") or f"Vehicle #{v.get('vehicleId')}", v.get("vehicleId"))

    def _on_next(self):
        if not self.trip:
            self.error_label.setText("Click a trip first.")
            return
        self._go(1)
        self._load_deliveries()

    # -- page 2: delivery + orders ----------------------------------------

    def _build_delivery_page(self) -> QWidget:
        page = QWidget()
        box = QVBoxLayout(page)
        box.setContentsMargins(0, 0, 0, 0)

        kind_word = "light" if self.kind == "light" else "bulk"
        box.addWidget(QLabel(f"Deliveries on this trip - pick one, or create a new {kind_word} delivery:"))
        self.delivery_list = QListWidget()
        self.delivery_list.setMaximumHeight(150)
        self.delivery_list.currentRowChanged.connect(lambda _row: self._on_delivery_choice())
        box.addWidget(self.delivery_list)

        details = QHBoxLayout()
        self.delivered_to_input = QLineEdit(self.group.get("customer") or "")
        self.delivered_to_input.setPlaceholderText("Delivered to (customer / destination)")
        self.delivered_to_input.setVisible(self.kind == "bulk")
        details.addWidget(self.delivered_to_input, 2)
        details.addWidget(QLabel("Vehicle"))
        self.vehicle_combo = QComboBox()
        details.addWidget(self.vehicle_combo, 1)
        details.addWidget(QLabel("Delivery date"))
        self.date_field = DatePickerField(None)
        details.addWidget(self.date_field, 2)
        box.addLayout(details)

        box.addWidget(QLabel("Orders to put on this delivery (untick any to leave them pending):"))
        self.checks: list[tuple[QCheckBox, dict]] = []
        for order in self.group.get("orders") or []:
            text = (
                f"Order #{order.get('orderId')}  -  {order.get('itemsText') or 'no items'}  -  "
                f"{_fmt_feet(order.get('bodyFeet'))} body ft  -  {'LARGE' if order.get('large') else 'small'}  -  KES {order.get('total')}"
            )
            check = QCheckBox(text)
            check.setChecked(True)
            box.addWidget(check)
            self.checks.append((check, order))
        box.addStretch()
        return page

    def _load_deliveries(self):
        self.delivery_list.clear()
        self.delivery_list.addItem("Loading...")
        self._run(
            lambda: self.api_client.get(f"/trips/{self.trip.get('tripId')}/deliveries"),
            self._show_deliveries,
            lambda message: self.error_label.setText(f"Couldn't load this trip's deliveries: {message}"),
        )

    def _show_deliveries(self, data):
        data = data or {}
        light_rows = data.get("light") or []
        self.delivery_list.clear()

        new_word = "light" if self.kind == "light" else "bulk"
        new_item = QListWidgetItem(f"+  Create a new {new_word} delivery")
        new_item.setData(Qt.ItemDataRole.UserRole, {"new": True})
        self.delivery_list.addItem(new_item)

        if self.kind == "light":
            # "if a delivery was completed in the mobile then dont allow it
            # to be assigned new orders since it means it was completed so
            # you either create a new one or use one still active" - a
            # delivered head has already gone out, so it drops off the
            # pick list entirely (the backend rejects it too either way).
            heads = [
                r for r in light_rows
                if not r.get("parentLightDeliveryId") and not r.get("bulkDeliveryId") and r.get("status") != "DELIVERED"
            ]
            for head in heads:
                kids = [
                    r for r in light_rows
                    if r.get("parentLightDeliveryId") == head.get("lightDeliveryId") and r.get("status") != "DELIVERED"
                ]
                count = len(kids) + (1 if head.get("total") and head.get("status") != "DELIVERED" else 0)
                item = QListWidgetItem(
                    f"Light delivery #{head.get('lightDeliveryId')}  -  {count} order(s)  -  "
                    f"{head.get('estimatedDeliveryDate') or 'no date'}  -  {head.get('status')}"
                )
                item.setData(Qt.ItemDataRole.UserRole, {"new": False, "light": head})
                self.delivery_list.addItem(item)
        else:
            # Same rule for bulk: one already delivered (Delivered_At set)
            # isn't a valid target for a new side quest either.
            for bulk in data.get("bulk") or []:
                if bulk.get("deliveredAt"):
                    continue
                kids = [
                    r for r in light_rows
                    if r.get("bulkDeliveryId") == bulk.get("bulkDeliveryId") and r.get("status") != "DELIVERED"
                ]
                item = QListWidgetItem(
                    f"Bulk delivery #{bulk.get('bulkDeliveryId')}  -  {bulk.get('deliveredTo') or ''}  -  {len(kids)} side quest(s)"
                )
                item.setData(Qt.ItemDataRole.UserRole, {"new": False, "bulk": bulk})
                self.delivery_list.addItem(item)

        # Default: an existing delivery if there is one, otherwise create.
        self.delivery_list.setCurrentRow(1 if self.delivery_list.count() > 1 else 0)
        self._on_delivery_choice()

    def _selected_choice(self) -> dict | None:
        item = self.delivery_list.currentItem()
        return item.data(Qt.ItemDataRole.UserRole) if item else None

    def _on_delivery_choice(self):
        choice = self._selected_choice()
        if not choice:
            return
        creating = choice.get("new")
        self.delivered_to_input.setEnabled(bool(creating) or self.kind != "bulk")
        # A trip with no end date yet (still ongoing) left this field blank,
        # so "Confirm Assignment" quietly refused with a small error label
        # and nothing visibly happened - default to today instead, still
        # editable, so creating a delivery never silently stalls on a date.
        fallback_date = date.today().isoformat()
        if creating:
            self.date_field.set_value((self.trip or {}).get("endDate") or fallback_date)
            return
        if self.kind == "light":
            head = choice["light"]
            self.date_field.set_value(
                head.get("estimatedDeliveryDate") or (self.trip or {}).get("endDate") or fallback_date
            )
            idx = self.vehicle_combo.findData(head.get("vehicleId"))
            self.vehicle_combo.setCurrentIndex(idx if idx >= 0 else 0)
        else:
            self.date_field.set_value((self.trip or {}).get("endDate") or fallback_date)

    # -- confirm ----------------------------------------------------------

    def _confirm(self):
        choice = self._selected_choice()
        if not choice:
            self.error_label.setText("Pick a delivery, or choose to create a new one.")
            return
        ids = [order.get("lightDeliveryId") for check, order in self.checks if check.isChecked()]
        if not ids:
            self.error_label.setText("Tick at least one order to put on the delivery.")
            return
        date = self.date_field.value()
        if not date:
            self.error_label.setText("Pick the delivery date - it's what the customer will see.")
            return
        if choice.get("new") and self.kind == "bulk" and not self.delivered_to_input.text().strip():
            self.error_label.setText("Say who the bulk delivery is going to.")
            return

        self.error_label.setText("")
        vehicle_id = self.vehicle_combo.currentData()
        trip_id = self.trip.get("tripId")
        delivered_to = self.delivered_to_input.text().strip()
        set_busy(self.confirm_btn, True, "Assigning")
        self.back_btn.setEnabled(False)
        self.cancel_btn.setEnabled(False)

        def work():
            target_id = None
            if choice.get("new"):
                # A delivery already created by an earlier attempt that then
                # failed is reused rather than creating a second empty one.
                if self._created_id is None:
                    if self.kind == "light":
                        created = self.api_client.post(
                            "/deliveries/light",
                            json={"tripId": trip_id, "vehicleId": vehicle_id, "deliveryAreaId": self.group.get("areaId"), "estimatedDeliveryDate": date},
                            actor=True,
                        )
                        self._created_id = created.get("lightDeliveryId")
                    else:
                        created = self.api_client.post(
                            "/deliveries/bulk", json={"deliveredTo": delivered_to, "tripId": trip_id}, actor=True
                        )
                        self._created_id = created.get("bulkDeliveryId")
                target_id = self._created_id
            else:
                target_id = choice["light"].get("lightDeliveryId") if self.kind == "light" else choice["bulk"].get("bulkDeliveryId")

            payload = {"lightDeliveryIds": ids, "tripId": trip_id, "vehicleId": vehicle_id, "estimatedDeliveryDate": date}
            payload["parentLightDeliveryId" if self.kind == "light" else "bulkDeliveryId"] = target_id
            return self.api_client.post("/deliveries/light/assign-batch", json=payload, actor=True)

        def done(_result):
            set_busy(self.confirm_btn, False)
            self.accept()

        def failed(message: str):
            set_busy(self.confirm_btn, False)
            self.back_btn.setEnabled(True)
            self.cancel_btn.setEnabled(True)
            self.error_label.setText(message)

        self._run(work, done, failed)


class AddToBatchDialog(QDialog):
    """Put a customer's new order(s) onto the batch they already have - same
    trip, vehicle and delivery date, so the customer's orders travel as one."""

    def __init__(self, api_client, group: dict, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.group = group
        self._active_workers = []
        self.setWindowTitle(f"Add to Existing Batch - {group.get('customer') or 'customer'}")
        self.setMinimumWidth(560)

        layout = QVBoxLayout(self)
        intro = QLabel("Pick the batch this customer already has, and tick the new orders worth adding to it.")
        intro.setWordWrap(True)
        intro.setProperty("role", "muted")
        layout.addWidget(intro)

        self.batch_combo = QComboBox()
        for batch in group.get("assignedBatches") or []:
            word = "Bulk" if batch.get("kind") == "bulk" else "Light"
            self.batch_combo.addItem(
                f"{word} delivery #{batch.get('targetId')}  -  trip #{batch.get('tripId')}  -  {batch.get('date') or 'no date'}"
                f"  -  {len(batch.get('orders') or [])} order(s) already on it",
                batch,
            )
        layout.addWidget(self.batch_combo)

        self.checks: list[tuple[QCheckBox, dict]] = []
        for order in group.get("orders") or []:
            check = QCheckBox(
                f"Order #{order.get('orderId')}  -  {order.get('itemsText') or 'no items'}  -  "
                f"{'LARGE' if order.get('large') else 'small'}  -  KES {order.get('total')}"
            )
            check.setChecked(True)
            layout.addWidget(check)
            self.checks.append((check, order))

        self.error_label = QLabel("")
        self.error_label.setProperty("role", "error")
        self.error_label.setWordWrap(True)
        layout.addWidget(self.error_label)

        row = QHBoxLayout()
        row.addStretch()
        self.cancel_btn = QPushButton("Cancel")
        self.cancel_btn.clicked.connect(self.reject)
        row.addWidget(self.cancel_btn)
        self.confirm_btn = QPushButton("Add to Batch")
        self.confirm_btn.setProperty("role", "primary")
        self.confirm_btn.clicked.connect(self._confirm)
        row.addWidget(self.confirm_btn)
        layout.addLayout(row)

    def _confirm(self):
        batch = self.batch_combo.currentData()
        ids = [order.get("lightDeliveryId") for check, order in self.checks if check.isChecked()]
        if not batch or not ids:
            self.error_label.setText("Tick at least one order to add.")
            return
        self.error_label.setText("")
        payload = {
            "lightDeliveryIds": ids,
            "tripId": batch.get("tripId"),
            "vehicleId": batch.get("vehicleId"),
            "estimatedDeliveryDate": batch.get("date"),
        }
        payload["bulkDeliveryId" if batch.get("kind") == "bulk" else "parentLightDeliveryId"] = batch.get("targetId")
        set_busy(self.confirm_btn, True, "Adding")
        self.cancel_btn.setEnabled(False)

        def done(_result):
            set_busy(self.confirm_btn, False)
            self.accept()

        def failed(message: str):
            set_busy(self.confirm_btn, False)
            self.cancel_btn.setEnabled(True)
            self.error_label.setText(message)

        worker = Worker(lambda: self.api_client.post("/deliveries/light/assign-batch", json=payload, actor=True))
        worker.signals.finished.connect(done)
        worker.signals.error.connect(failed)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)


class DropOrderDialog(QDialog):
    """Drop a not-yet-assigned order and, right away, offer to pay its
    refund out - "add the drop and refund option so the desktop admin can
    also refund the cash". Mirrors the mobile app's own dropOrder(orderId,
    cost): refund = total - cancellation cost, stock goes back, the order
    is cancelled - then Send Money (ExpenseTarget.ORDER_REFUND) pays the
    customer, same PayoutDialog every other category already uses."""

    def __init__(self, api_client, order: dict, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.order = order
        self.order_id = order.get("orderId")
        self.refunded_amount = None
        self._active_workers = []
        self.setWindowTitle(f"Drop Order #{self.order_id}")
        self.setMinimumWidth(460)

        layout = QVBoxLayout(self)
        intro = QLabel(
            f"Order #{self.order_id} - {order.get('goods') or 'no items'} - KES {order.get('total (KES)')}.\n"
            "Dropping it cancels the order and restores the stock. Enter what to deduct (e.g. handling already "
            "done) - the rest is the refund owed to the customer."
        )
        intro.setWordWrap(True)
        intro.setProperty("role", "muted")
        layout.addWidget(intro)

        form = QHBoxLayout()
        form.addWidget(QLabel("Cancellation cost (KES)"))
        self.cost_input = QLineEdit("0")
        form.addWidget(self.cost_input)
        layout.addLayout(form)

        self.result_label = QLabel("")
        self.result_label.setWordWrap(True)
        layout.addWidget(self.result_label)
        self.error_label = QLabel("")
        self.error_label.setProperty("role", "error")
        self.error_label.setWordWrap(True)
        layout.addWidget(self.error_label)

        row = QHBoxLayout()
        row.addStretch()
        self.cancel_btn = QPushButton("Cancel")
        self.cancel_btn.clicked.connect(self.reject)
        row.addWidget(self.cancel_btn)
        self.drop_btn = QPushButton("Drop Order")
        self.drop_btn.setProperty("role", "danger")
        self.drop_btn.clicked.connect(self._drop)
        row.addWidget(self.drop_btn)
        self.refund_btn = QPushButton("Refund Now")
        self.refund_btn.setProperty("role", "primary")
        self.refund_btn.setVisible(False)
        self.refund_btn.clicked.connect(self._open_refund)
        row.addWidget(self.refund_btn)
        layout.addLayout(row)

    def _run(self, fn, on_done, on_error):
        worker = Worker(fn)
        worker.signals.finished.connect(on_done)
        worker.signals.error.connect(on_error)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)

    def _drop(self):
        try:
            cost = float(self.cost_input.text().strip() or 0)
        except ValueError:
            self.error_label.setText("Enter a valid cancellation cost.")
            return
        self.error_label.setText("")
        set_busy(self.drop_btn, True, "Dropping")
        self.cancel_btn.setEnabled(False)

        def done(result):
            set_busy(self.drop_btn, False)
            self.drop_btn.setEnabled(False)
            self.cost_input.setEnabled(False)
            self.refunded_amount = (result or {}).get("refundedAmount")
            self.result_label.setText(
                f"Dropped - refund owed: KES {self.refunded_amount}. Stock has been restored."
            )
            self.refund_btn.setVisible(True)
            self.cancel_btn.setText("Close")
            self.cancel_btn.setEnabled(True)
            self.cancel_btn.clicked.disconnect()
            self.cancel_btn.clicked.connect(self.accept)

        def failed(message: str):
            set_busy(self.drop_btn, False)
            self.cancel_btn.setEnabled(True)
            self.error_label.setText(message)

        self._run(
            lambda: self.api_client.post(f"/orders/{self.order_id}/drop", json={"cancellationCost": cost}),
            done, failed,
        )

    def _open_refund(self):
        dialog = PayoutDialog(
            self.api_client,
            f"Refund Order #{self.order_id} - Send Money",
            purpose=f"Refund for Order #{self.order_id}",
            recipient_target="ORDER_REFUND",
            recipient_row_id=self.order_id,
            send_fn=lambda amount, phone: self.api_client.post(
                "/transactions/send-money",
                json={"amount": amount, "phoneNumber": phone, "target": "ORDER_REFUND", "targetRowId": self.order_id},
                actor=True,
                timeout=85,
            ),
            prefilled_amount=self.refunded_amount,
            parent=self,
        )
        dialog.exec()
