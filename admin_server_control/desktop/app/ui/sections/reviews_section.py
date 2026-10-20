from PySide6.QtCore import QThreadPool
from PySide6.QtGui import QColor
from PySide6.QtWidgets import QHBoxLayout, QLabel, QMessageBox, QPushButton, QVBoxLayout, QWidget

from app.core.worker import Worker
from app.ui.widgets.data_table import DataTable

HIDDEN_TINT = QColor(120, 40, 40, 70)


class ReviewsSection(QWidget):
    """Product ratings customers gave after delivery (see the backend's
    ReviewService). Admins see who wrote each one; the website only ever
    shows the first name + initial of customers who agreed to go public.
    Hide takes an abusive or false review off the product page and out of
    its stars - Show puts it back. Admins can't edit what a customer wrote."""

    def __init__(self, api_client, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.selected: dict | None = None
        self._active_workers = []

        layout = QVBoxLayout(self)

        self.table = DataTable("Product Ratings", show_limit_selector=False, searchable=True, search_placeholder="Search by product, customer or comment")
        self.table.hidden_columns = {"reviewId", "hidden"}
        self.table.refresh_requested.connect(self._load)
        self.table.row_selected.connect(self._on_row_selected)
        layout.addWidget(self.table)

        action_row = QHBoxLayout()
        self.selection_label = QLabel("Double-click a rating to select it. Hidden ratings are tinted red.")
        self.selection_label.setProperty("role", "muted")
        action_row.addWidget(self.selection_label)
        action_row.addStretch()

        self.hide_btn = QPushButton("Hide from website")
        self.hide_btn.setEnabled(False)
        self.hide_btn.clicked.connect(self._toggle_hidden)
        action_row.addWidget(self.hide_btn)

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
        self.selected = None
        self.hide_btn.setEnabled(False)
        self._run(lambda: self.api_client.get("/reviews/admin"), self._show_rows, self.table.set_error)

    def _show_rows(self, rows):
        shaped = []
        for i, row in enumerate(rows or [], start=1):
            shaped.append({
                "#": i,
                "reviewId": row.get("reviewId"),
                "hidden": row.get("hidden"),
                "product": row.get("product"),
                "customer": row.get("customer"),
                "stars": "★" * int(row.get("rating") or 0),
                "comment": row.get("comment") or "",
                "visibility": row.get("visibility"),
                "status": "Hidden" if row.get("hidden") else "Shown",
                "ratedAt": row.get("ratedAt"),
            })
        self.table.set_rows(shaped, row_color=lambda row: HIDDEN_TINT if row.get("hidden") else None)

    def _on_row_selected(self, row: dict):
        self.selected = row
        hidden = bool(row.get("hidden"))
        self.selection_label.setText(f"Selected: {row.get('customer')} on {row.get('product')} ({row.get('stars')})")
        self.hide_btn.setText("Show on website again" if hidden else "Hide from website")
        self.hide_btn.setEnabled(True)

    def _toggle_hidden(self):
        if not self.selected:
            return
        review_id = self.selected.get("reviewId")
        hide = not bool(self.selected.get("hidden"))
        question = (
            "Hide this rating? It disappears from the product page and stops counting in the product's stars. "
            "The customer still sees it on their own page, marked as hidden."
            if hide
            else "Show this rating on the website again?"
        )
        if QMessageBox.question(self, "Product rating", question) != QMessageBox.StandardButton.Yes:
            return
        self.hide_btn.setEnabled(False)
        self._run(
            lambda: self.api_client.post(f"/reviews/admin/{review_id}/hidden", json={"hidden": hide}),
            lambda _result: self._load(None),
            self._on_error,
        )

    def _on_error(self, message: str):
        self.hide_btn.setEnabled(True)
        QMessageBox.warning(self, "Product rating", f"Couldn't update the rating: {message}")
