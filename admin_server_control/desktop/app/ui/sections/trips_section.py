from PySide6.QtWidgets import QHBoxLayout, QLabel, QMessageBox, QPushButton, QVBoxLayout, QWidget

from app.ui.sections.generic_list_section import GenericListSection
from app.ui.sections.trip_detail_dialog import TripDetailDialog
from app.ui.widgets.record_form_dialog import FieldSpec, RecordFormDialog


class TripsSection(QWidget):
    def __init__(self, api_client, parent=None):
        super().__init__(parent)
        self.api_client = api_client

        layout = QVBoxLayout(self)

        # "an area to allow one to create a new trip not just selecting
        # the already preexisting trips" - everything else here (deliveries,
        # loans, capital, trees) hangs off a trip, so it has to exist first.
        actions_row = QHBoxLayout()
        hint = QLabel("Double-click a trip for its full picture - summary, loans, and both delivery types.")
        hint.setProperty("role", "muted")
        actions_row.addWidget(hint)
        actions_row.addStretch()
        self.new_trip_btn = QPushButton("New Trip")
        self.new_trip_btn.setProperty("role", "primary")
        self.new_trip_btn.clicked.connect(self._open_create_trip)
        actions_row.addWidget(self.new_trip_btn)
        layout.addLayout(actions_row)

        # "i should see all the trips nothing hidden not unless i select
        # the 50 only etc" - unlike mobile (capped at 6 to keep its own
        # server light), desktop's /trips is unlimited; this just defaults
        # the selector to "All" instead of the usual capped-at-50 default.
        self.list_section = GenericListSection(
            "Trips",
            self._fetch_trips,
            show_limit_selector=True,
            default_limit_label="All",
            searchable=True,
            search_placeholder="Search trips",
        )
        self.list_section.table.row_selected.connect(self._open_trip)
        layout.addWidget(self.list_section)

    def _fetch_trips(self, limit):
        rows = self.api_client.get("/trips", params={"limit": limit} if limit else None) or []
        # OPEN until it has been through Finalize Trip.
        return [{"tripId": t.get("tripId"), "state": "FINALIZED" if t.get("endDate") else "OPEN",
                 **{k: v for k, v in t.items() if k != "tripId"}} for t in rows]

    def _open_create_trip(self):
        # "you can never create a new trip before you finalize every detail in
        # the other trip" - checked up front so nobody fills in a form the
        # server is going to refuse.
        try:
            open_trip = (self.api_client.get("/trips/open") or {}).get("openTrip")
        except Exception:
            open_trip = None
        if open_trip:
            box = QMessageBox(self)
            box.setWindowTitle("Finalize the open trip first")
            box.setText(
                f"Trip #{open_trip.get('tripId')} is still open.\n\nBefore a new trip can start, every order on it must be "
                "delivered or dropped to the next trip, its loans settled, and its profit recorded - all of that happens "
                "when you Finalize Trip."
            )
            open_btn = box.addButton(f"Open Trip #{open_trip.get('tripId')}", QMessageBox.ButtonRole.AcceptRole)
            box.addButton("Close", QMessageBox.ButtonRole.RejectRole)
            box.exec()
            if box.clickedButton() is open_btn:
                self._open_trip(open_trip)
            return
        self._show_create_form()

    def _show_create_form(self):
        fields = [
            FieldSpec("startDate", "Start date", required=True, type="date"),
            FieldSpec("productDescription", "Product description", type="textarea"),
        ]
        dialog = RecordFormDialog(
            "New Trip",
            fields,
            save_fn=lambda values: self.api_client.post("/trips", json=values),
            parent=self,
        )
        if dialog.exec():
            self.refresh()

    def _open_trip(self, trip: dict):
        dialog = TripDetailDialog(self.api_client, trip, parent=self)
        dialog.exec()
        # The dialog can delete the trip - always reload the list on the way out.
        self.refresh()

    def refresh(self):
        self.list_section.load(self.list_section.table.current_limit())
