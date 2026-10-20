from PySide6.QtCore import QThreadPool, Qt
from PySide6.QtGui import QColor
from PySide6.QtWidgets import QCheckBox, QHBoxLayout, QLabel, QMessageBox, QPushButton, QVBoxLayout, QWidget

from app.core.worker import Worker
from app.ui import theme
from app.ui.widgets.data_table import DataTable
from app.ui.widgets.record_form_dialog import FieldSpec, RecordFormDialog

STATIC_FIELDS = [
    FieldSpec("name", "Name", required=True),
    FieldSpec("sku", "SKU"),
    FieldSpec("type", "Type"),
    FieldSpec("description", "Description", type="textarea"),
    FieldSpec("price", "Price (KES)", type="float", required=True, maximum=100_000_000),
    FieldSpec("stock", "Stock", type="float", maximum=100_000_000),
    FieldSpec(
        "unit", "Unit", type="combo", options=[("Pieces", "pieces"), ("Board feet", "board_feet")]
    ),
    FieldSpec("attributes", "Attributes", placeholder="e.g. length, grade"),
    FieldSpec("marketerNote", "Promo tag", placeholder='e.g. "Most Ordered - Only 2 left"'),
]

# A retired product is tinted a muted wood-brown so it stands apart from the
# live rows (green) and the deleted rows (red) at a glance.
# Retired rows: theme.RETIRED_TINT (a wood-brown that suits each theme).


class ProductsSection(QWidget):
    """Full CRUD for Products - view (with Deleted and Out-of-stock toggles),
    create, edit, delete/restore and retire/bring-back. Retiring hides a
    product from the shop without touching its stock or existing orders, and
    it can be brought back. When products run out of stock, a banner invites
    the admin to review and retire them."""

    def __init__(self, api_client, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.selected: dict | None = None
        self.showing_deleted = False
        self.showing_depleted = False
        self.depleted_count = 0
        self.trees: list[dict] = []
        self._active_workers = []

        layout = QVBoxLayout(self)

        # "in an event where the stock is depleted the admins are notified" -
        # a banner that appears only while something has run out, and opens
        # the out-of-stock list in a tap. It doubles as the way back out of
        # that list.
        self.alert_btn = QPushButton()
        self.alert_btn.setVisible(False)
        self.alert_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        self.alert_btn.clicked.connect(self._on_alert_clicked)
        self._style_alert()
        layout.addWidget(self.alert_btn)

        self.table = DataTable("Products", searchable=True, search_placeholder="Search products by name, type or SKU")
        self.table.hidden_columns = {"retiredAt", "retiredBy"}
        self.table.refresh_requested.connect(self._load)
        self.table.row_selected.connect(self._on_row_selected)
        layout.addWidget(self.table)

        action_row = QHBoxLayout()
        self.selection_label = QLabel("Double-click a product to select it.")
        self.selection_label.setProperty("role", "muted")
        action_row.addWidget(self.selection_label)
        action_row.addStretch()

        self.deleted_toggle = QCheckBox("Show deleted")
        self.deleted_toggle.toggled.connect(self._on_toggle_deleted)
        action_row.addWidget(self.deleted_toggle)

        self.new_btn = QPushButton("New Product")
        self.new_btn.setProperty("role", "primary")
        self.new_btn.clicked.connect(self._open_create)
        action_row.addWidget(self.new_btn)

        self.edit_btn = QPushButton("Edit")
        self.edit_btn.setEnabled(False)
        self.edit_btn.clicked.connect(self._open_edit)
        action_row.addWidget(self.edit_btn)

        # Retiring is reversible, so it sits with Edit rather than by Delete.
        self.retire_btn = QPushButton("Retire")
        self.retire_btn.setEnabled(False)
        self.retire_btn.clicked.connect(self._retire_or_bring_back)
        action_row.addWidget(self.retire_btn)

        # "an admin having edit button near delete is risky" - real
        # breathing room between them, not just visual order, so a misclick
        # can't land on Delete while reaching for Edit.
        action_row.addSpacing(28)

        self.delete_btn = QPushButton("Delete")
        self.delete_btn.setProperty("role", "danger")
        self.delete_btn.setEnabled(False)
        self.delete_btn.clicked.connect(self._delete_selected)
        action_row.addWidget(self.delete_btn)

        self.restore_btn = QPushButton("Restore")
        self.restore_btn.setEnabled(False)
        self.restore_btn.setVisible(False)
        self.restore_btn.clicked.connect(self._restore_selected)
        action_row.addWidget(self.restore_btn)

        layout.addLayout(action_row)

        self._load(self.table.current_limit())
        self._load_trees()

    def _style_alert(self):
        self.alert_btn.setStyleSheet(
            f"QPushButton {{ text-align: left; padding: 9px 14px; border-radius: 8px; "
            f"background-color: rgba(235,154,75,0.16); border: 1px solid {theme.WOOD}; color: {theme.ACCENT if theme.IS_LIGHT else theme.WOOD_LIGHT}; }}"
            f"QPushButton:hover {{ background-color: rgba(235,154,75,0.26); }}"
        )

    def _run(self, fn, on_done, on_error):
        worker = Worker(fn)
        worker.signals.finished.connect(on_done)
        worker.signals.error.connect(on_error)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)

    def _load_trees(self):
        # "allow one to edit the tree id based on the name of the tree but
        # the id is still ok ... for simplification" - Products isn't
        # trip-scoped, so this pulls every tree (tripId omitted = all,
        # per TreeService.list) rather than filtering to one trip.
        self._run(lambda: self.api_client.get("/trees"), self._on_trees_loaded, lambda _msg: None)

    def _on_trees_loaded(self, trees):
        self.trees = trees or []

    def _tree_options(self):
        return [("(none)", None)] + [
            (
                f"{t.get('houseHold') or 'no household'} - Trip {t.get('tripId')} (#{t.get('treesId')})",
                t.get("treesId"),
            )
            for t in self.trees
        ]

    def _fields(self):
        # "the ability to ... pick a new media ... when creating a new
        # product since i can be having the video or photo here in the
        # laptop" - the photo is chosen on this computer (it used to be a
        # typed media key) and uploaded on Save, named after the product.
        photo = FieldSpec("image", "Photo", type="media", upload=self._upload_photo)
        return [photo] + STATIC_FIELDS + [
            FieldSpec("treesId", "Source tree", type="combo", options=self._tree_options())
        ]

    def _upload_photo(self, pending, values):
        body, content_type = pending.upload_body()
        return self.api_client.post_file(
            "/uploads/products", body, content_type, params={"name": values.get("name") or "product"}
        )

    def _row_color(self, row):
        # Deleted rows are tinted red by the table itself; retired (but not
        # deleted) rows get the wood-brown tint here.
        if row.get("isRetired") and not (row.get("isDeleted") or row.get("Is_Deleted")):
            return QColor(theme.RETIRED_TINT)
        return None

    def _load(self, limit):
        self.table.set_loading()
        if self.showing_deleted:
            path, params = "/products/deleted", None
        elif self.showing_depleted:
            path, params = "/products/depleted", None
        else:
            path = "/products"
            params = {"limit": limit} if limit else None
        self._run(
            lambda: self.api_client.get(path, params=params),
            lambda rows: self.table.set_rows(rows or [], row_color=self._row_color),
            self.table.set_error,
        )
        # Keep the out-of-stock banner current (except while already in that
        # list, where the table itself is the review).
        if not self.showing_depleted:
            self._refresh_alert()

    def _refresh_alert(self):
        self._run(
            lambda: self.api_client.get("/products/depleted"),
            self._on_depleted_count,
            lambda _msg: None,
        )

    def _on_depleted_count(self, rows):
        self.depleted_count = len(rows or [])
        self._update_alert()

    def _update_alert(self):
        if self.showing_depleted:
            self.alert_btn.setText("←  Back to all products")
            self.alert_btn.setVisible(True)
        elif self.depleted_count > 0:
            n = self.depleted_count
            self.alert_btn.setText(f"Needs attention: {n} product{'s' if n != 1 else ''} out of stock — review and retire if needed")
            self.alert_btn.setVisible(True)
        else:
            self.alert_btn.setVisible(False)

    def _clear_selection(self):
        self.selected = None
        self.selection_label.setText("Double-click a product to select it.")
        self._update_action_state()

    def _on_alert_clicked(self):
        # Toggle into / out of the out-of-stock list.
        self.showing_depleted = not self.showing_depleted
        if self.showing_depleted:
            self.showing_deleted = False
            self.deleted_toggle.setChecked(False)
        self._clear_selection()
        self._update_alert()
        self._load(self.table.current_limit())

    def _on_toggle_deleted(self, checked: bool):
        self.showing_deleted = checked
        if checked:
            self.showing_depleted = False
        self.delete_btn.setVisible(not checked)
        self.restore_btn.setVisible(checked)
        self.retire_btn.setVisible(not checked)
        self._clear_selection()
        self._update_alert()
        self._load(self.table.current_limit())

    def _on_row_selected(self, row: dict):
        self.selected = row
        mark = " - retired" if row.get("isRetired") else ""
        self.selection_label.setText(f"Selected: {row.get('name')} (#{row.get('productId')}){mark}")
        self._update_action_state()

    def _update_action_state(self):
        has_selection = self.selected is not None
        self.edit_btn.setEnabled(has_selection and not self.showing_deleted)
        self.delete_btn.setEnabled(has_selection and not self.showing_deleted)
        self.restore_btn.setEnabled(has_selection and self.showing_deleted)
        self.retire_btn.setEnabled(has_selection and not self.showing_deleted)
        # The button says what it will do to the selected product.
        self.retire_btn.setText("Bring back" if (self.selected and self.selected.get("isRetired")) else "Retire")

    def _open_create(self):
        dialog = RecordFormDialog(
            "New Product",
            self._fields(),
            save_fn=lambda values: self.api_client.post("/products", json=values, actor=True),
            parent=self,
        )
        if dialog.exec():
            self._load(self.table.current_limit())

    def _open_edit(self):
        if not self.selected:
            return
        product_id = self.selected.get("productId")
        dialog = RecordFormDialog(
            f"Edit Product #{product_id}",
            self._fields(),
            save_fn=lambda values: self.api_client.patch(f"/products/{product_id}", json=values, actor=True),
            initial_values=self.selected,
            parent=self,
        )
        if dialog.exec():
            self._load(self.table.current_limit())

    def _retire_or_bring_back(self):
        if not self.selected:
            return
        product_id = self.selected.get("productId")
        name = self.selected.get("name")
        if self.selected.get("isRetired"):
            title, question, path = (
                "Bring product back?",
                f"Bring '{name}' back into the shop? Customers will be able to find and order it again.",
                f"/products/{product_id}/unretire",
            )
        else:
            title, question, path = (
                "Retire product?",
                f"Retire '{name}'? It will be hidden from the shop and can't be ordered. "
                "Its stock and any existing orders are kept, and you can bring it back any time.",
                f"/products/{product_id}/retire",
            )
        if QMessageBox.question(self, title, question) != QMessageBox.StandardButton.Yes:
            return

        def on_done(_result):
            self._clear_selection()
            self._load(self.table.current_limit())

        self._run(
            lambda: self.api_client.post(path, actor=True),
            on_done,
            lambda msg: QMessageBox.critical(self, "Couldn't update product", msg),
        )

    def _delete_selected(self):
        if not self.selected:
            return
        product_id = self.selected.get("productId")
        name = self.selected.get("name")
        confirm = QMessageBox.question(self, "Delete product?", f"Delete '{name}'? This is a soft delete - it can be restored.")
        if confirm != QMessageBox.StandardButton.Yes:
            return

        def on_done(_result):
            self.selected = None
            self._update_action_state()
            self._load(self.table.current_limit())

        self._run(
            lambda: self.api_client.delete(f"/products/{product_id}", actor=True),
            on_done,
            lambda msg: QMessageBox.critical(self, "Couldn't delete product", msg),
        )

    def _restore_selected(self):
        if not self.selected:
            return
        product_id = self.selected.get("productId")

        def on_done(_result):
            self.selected = None
            self._update_action_state()
            self._load(self.table.current_limit())

        self._run(
            lambda: self.api_client.post(f"/products/{product_id}/restore", actor=True),
            on_done,
            lambda msg: QMessageBox.critical(self, "Couldn't restore product", msg),
        )
