import webbrowser
from urllib.parse import quote

from PySide6.QtCore import QThreadPool
from PySide6.QtWidgets import QHBoxLayout, QLabel, QPushButton, QVBoxLayout, QWidget

from app.core.worker import Worker
from app.ui.widgets.data_table import DataTable
from app.ui.widgets.record_form_dialog import FieldSpec, RecordFormDialog
from app.ui import theme
from app.ui.widgets.data_table import CellNote

# "typing nairobi and Nairobi was recognized as a different city so lets
# fix this by a drop down of all cities" - Kenya's 47 counties, picked
# instead of free-typed, so the same place is always spelled the same way.
KENYA_COUNTIES = [
    "Baringo", "Bomet", "Bungoma", "Busia", "Elgeyo-Marakwet", "Embu", "Garissa", "Homa Bay", "Isiolo", "Kajiado",
    "Kakamega", "Kericho", "Kiambu", "Kilifi", "Kirinyaga", "Kisii", "Kisumu", "Kitui", "Kwale", "Laikipia", "Lamu",
    "Machakos", "Makueni", "Mandera", "Marsabit", "Meru", "Migori", "Mombasa", "Murang'a", "Nairobi", "Nakuru",
    "Nandi", "Narok", "Nyamira", "Nyandarua", "Nyeri", "Samburu", "Siaya", "Taita-Taveta", "Tana River",
    "Tharaka-Nithi", "Trans-Nzoia", "Turkana", "Uasin Gishu", "Vihiga", "Wajir", "West Pokot",
]
CITY_OPTIONS = [(county, county) for county in KENYA_COUNTIES]

FIELDS = [
    FieldSpec("city", "City / County", type="combo", required=True, options=CITY_OPTIONS),
    FieldSpec("areaName", "Area name", required=True),
    FieldSpec("description", "Description", type="textarea"),
    # "for each delivery area in the database lets add a field to add like
    # google maps coordinates of the location ... open e.g google maps
    # select the exact location and save" - same "lat,long" text field
    # TREES.Coordinates already uses (see trip_detail_dialog.py's tree
    # form): open Google Maps yourself (the "Find on Google Maps" button
    # below), right-click the spot, copy the coordinates it shows, paste
    # them in here.
    FieldSpec("coordinates", "Coordinates (lat,long) - optional", placeholder="e.g. -1.286389,36.817223"),
    FieldSpec("isActive", "Active (shown at checkout)", type="checkbox"),
]


class DeliveryAreasSection(QWidget):
    """Create/edit delivery & pickup areas - no delete endpoint on the
    backend for these (switch isActive off instead, same as the mobile
    app's own management view)."""

    def __init__(self, api_client, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.selected: dict | None = None
        self._active_workers = []

        layout = QVBoxLayout(self)

        self.table = DataTable("Delivery / Pickup Areas", show_limit_selector=False, searchable=True, search_placeholder="Search by town or area")
        # "arrange the delivery places in a decent order from 1 to ... not 2
        # then 4 then 6" - a running number instead of the raw id (which
        # has gaps wherever an area was ever removed), oldest first.
        self.table.hidden_columns = {"deliveryAreaId"}
        self.table.refresh_requested.connect(self._load)
        self.table.row_selected.connect(self._on_row_selected)
        layout.addWidget(self.table)

        action_row = QHBoxLayout()
        self.selection_label = QLabel("Double-click an area to select it.")
        self.selection_label.setProperty("role", "muted")
        action_row.addWidget(self.selection_label)
        action_row.addStretch()

        self.new_btn = QPushButton("New Area")
        self.new_btn.setProperty("role", "primary")
        self.new_btn.clicked.connect(self._open_create)
        action_row.addWidget(self.new_btn)

        self.edit_btn = QPushButton("Edit")
        self.edit_btn.setEnabled(False)
        self.edit_btn.clicked.connect(self._open_edit)
        action_row.addWidget(self.edit_btn)

        self.maps_btn = QPushButton("Find on Google Maps")
        self.maps_btn.setEnabled(False)
        self.maps_btn.clicked.connect(self._open_maps)
        action_row.addWidget(self.maps_btn)

        layout.addLayout(action_row)

        self._load(None)

    def _run(self, fn, on_done, on_error):
        worker = Worker(fn)
        worker.signals.finished.connect(on_done)
        worker.signals.error.connect(on_error)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)

    def _load(self, _limit):
        self.table.set_loading()
        self._run(
            lambda: self.api_client.get("/shipping/areas"),
            self._show_rows,
            self.table.set_error,
        )

    def _show_rows(self, rows):
        ordered = sorted(rows or [], key=lambda r: r.get("deliveryAreaId") or 0)
        # "always make sure one can see the coordinates so one knows what
        # area needs to have its coordinates filled" - a missing value
        # rendered as a plain empty cell (DataTable's default for None) is
        # too easy to skim past in a long list; a loud, explicit flag
        # instead makes every not-yet-pinned area jump out at a glance.
        shaped = [{"#": i, **row} for i, row in enumerate(ordered, start=1)]
        self.table.set_rows(shaped, empty_cells={"coordinates": CellNote("Not set", "warning", theme.DANGER)})

    def _on_row_selected(self, row: dict):
        self.selected = row
        self.selection_label.setText(f"Selected: {row.get('areaName')}, {row.get('city')}")
        self.edit_btn.setEnabled(True)
        self.maps_btn.setEnabled(True)

    def _open_maps(self):
        if not self.selected:
            return
        coordinates = (self.selected.get("coordinates") or "").strip()
        if coordinates:
            # Already pinned - go straight to it.
            url = f"https://www.google.com/maps/search/?api=1&query={quote(coordinates)}"
        else:
            # Not pinned yet - search by name so the exact spot can be
            # found, then its coordinates copied back into the Edit form.
            query = f"{self.selected.get('areaName') or ''}, {self.selected.get('city') or ''}, Kenya"
            url = f"https://www.google.com/maps/search/?api=1&query={quote(query)}"
        webbrowser.open(url)

    def _open_create(self):
        dialog = RecordFormDialog(
            "New Delivery Area",
            FIELDS,
            save_fn=lambda values: self.api_client.post("/shipping/areas", json=values),
            initial_values={"isActive": True},
            parent=self,
        )
        if dialog.exec():
            self._load(None)

    def _open_edit(self):
        if not self.selected:
            return
        area_id = self.selected.get("deliveryAreaId")
        dialog = RecordFormDialog(
            f"Edit Area #{area_id}",
            FIELDS,
            save_fn=lambda values: self.api_client.patch(f"/shipping/areas/{area_id}", json=values),
            initial_values=self.selected,
            parent=self,
        )
        if dialog.exec():
            self._load(None)
