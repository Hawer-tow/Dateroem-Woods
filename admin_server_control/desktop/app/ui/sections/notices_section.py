from datetime import date

from PySide6.QtCore import QThreadPool
from PySide6.QtGui import QColor
from PySide6.QtWidgets import QCheckBox, QHBoxLayout, QLabel, QMessageBox, QPushButton, QVBoxLayout, QWidget

from app.core.worker import Worker
from app.ui import theme
from app.ui.widgets.data_table import DataTable
from app.ui.widgets.record_form_dialog import FieldSpec, RecordFormDialog

FIELDS = [
    FieldSpec("title", "Title", required=True, placeholder="e.g. New: pay for bulk orders online"),
    FieldSpec(
        "message", "Message", type="textarea", required=True, height=170,
        placeholder="What is coming or what has changed, the way a customer would want to read it.",
    ),
    FieldSpec("showUntil", "Last day it shows", type="date", required=True),
    FieldSpec("published", "Show on the website", type="checkbox"),
]


class NoticesSection(QWidget):
    """"an information area ... e.g we might be having an update soon or new
    features we can write them then we send them with a deadline for
    elapsing" - the notices behind the website's Updates button. Each one
    shows until the end of its last day, then leaves the website by itself;
    ended ones stay here, so one can be brought back by giving it a new
    date. Same layout as FAQs."""

    def __init__(self, api_client, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.notices: dict[int, dict] = {}
        self.selected: dict | None = None
        self.showing_deleted = False
        self._active_workers = []

        layout = QVBoxLayout(self)

        intro = QLabel(
            "Notices show on the website behind a small Updates (bell) button in the top bar, newest first, until the "
            "end of their last day. The button only appears while a notice is showing, with a dot for visitors who "
            "haven't read the newest one yet."
        )
        intro.setWordWrap(True)
        intro.setProperty("role", "muted")
        layout.addWidget(intro)

        self.table = DataTable(
            "Notices", show_limit_selector=False, searchable=True, search_placeholder="Search notices"
        )
        self.table.refresh_requested.connect(self._load)
        self.table.row_selected.connect(self._on_row_selected)
        layout.addWidget(self.table)

        action_row = QHBoxLayout()
        self.selection_label = QLabel("Double-click a notice to select it.")
        self.selection_label.setProperty("role", "muted")
        action_row.addWidget(self.selection_label)
        action_row.addStretch()

        self.deleted_toggle = QCheckBox("Show deleted")
        self.deleted_toggle.toggled.connect(self._on_toggle_deleted)
        action_row.addWidget(self.deleted_toggle)

        self.new_btn = QPushButton("New Notice")
        self.new_btn.setProperty("role", "primary")
        self.new_btn.clicked.connect(self._open_create)
        action_row.addWidget(self.new_btn)

        self.edit_btn = QPushButton("Edit")
        self.edit_btn.setEnabled(False)
        self.edit_btn.clicked.connect(self._open_edit)
        action_row.addWidget(self.edit_btn)

        # Same breathing room between Edit and Delete as in Products.
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
        self._load(None)

    def _run(self, fn, on_done, on_error):
        worker = Worker(fn)
        worker.signals.finished.connect(on_done)
        worker.signals.error.connect(on_error)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)

    def _load(self, _limit=None):
        self.table.set_loading()
        params = {"deleted": "true"} if self.showing_deleted else None
        self._run(lambda: self.api_client.get("/notices", params=params), self._on_loaded, self.table.set_error)

    @staticmethod
    def _state(notice: dict) -> str:
        if not notice.get("isPublished"):
            return "Draft"
        return "Showing" if notice.get("isCurrent") else "Ended"

    def _on_loaded(self, notices):
        notices = notices or []
        self.notices = {notice.get("noticeId"): notice for notice in notices}
        rows = [
            {
                "#": notice.get("noticeId"),
                "Title": notice.get("title"),
                "On the website": self._state(notice),
                "Last day": notice.get("showUntil"),
                "Message": " ".join(str(notice.get("message") or "").split())[:90],
                "Last changed": str(notice.get("updatedAt") or notice.get("createdAt") or "").replace("T", " ")[:16],
                "By": notice.get("updatedByName") or "",
            }
            for notice in notices
        ]
        self.table.set_rows(rows, row_color=self._row_color)
        self.selected = None
        self.selection_label.setText("Double-click a notice to select it.")
        self._update_action_state()

    def _row_color(self, row):
        # Ended and draft notices are kept but not on the website - the same
        # muted wood-brown as a retired product or campaign.
        if not self.showing_deleted and row.get("On the website") != "Showing":
            return QColor(theme.RETIRED_TINT)
        return None

    def _on_toggle_deleted(self, checked: bool):
        self.showing_deleted = checked
        self.delete_btn.setVisible(not checked)
        self.restore_btn.setVisible(checked)
        self.new_btn.setEnabled(not checked)
        self._load()

    def _on_row_selected(self, row: dict):
        self.selected = self.notices.get(row.get("#"))
        if self.selected:
            self.selection_label.setText(f"Selected: {self.selected.get('title')} ({self._state(self.selected).lower()})")
        self._update_action_state()

    def _update_action_state(self):
        has_selection = self.selected is not None
        self.edit_btn.setEnabled(has_selection and not self.showing_deleted)
        self.delete_btn.setEnabled(has_selection and not self.showing_deleted)
        self.restore_btn.setEnabled(has_selection and self.showing_deleted)

    def _open_create(self):
        dialog = RecordFormDialog(
            "New Notice",
            FIELDS,
            save_fn=lambda values: self.api_client.post("/notices", json=values),
            initial_values={"published": True, "showUntil": date.today().isoformat()},
            parent=self,
        )
        if dialog.exec():
            self._load()

    def _open_edit(self):
        notice = self.selected
        if not notice:
            return
        notice_id = notice.get("noticeId")
        dialog = RecordFormDialog(
            f"Edit Notice #{notice_id}",
            FIELDS,
            save_fn=lambda values: self.api_client.patch(f"/notices/{notice_id}", json=values),
            initial_values={**notice, "published": bool(notice.get("isPublished"))},
            parent=self,
        )
        if dialog.exec():
            self._load()

    def _delete_selected(self):
        notice = self.selected
        if not notice:
            return
        question = (
            f"Delete \"{notice.get('title')}\"?\n\nIt leaves the website now; it can be restored from Show deleted. "
            "A notice also leaves the website by itself after its last day."
        )
        if QMessageBox.question(self, "Delete notice?", question) != QMessageBox.StandardButton.Yes:
            return
        notice_id = notice.get("noticeId")
        self._run(
            lambda: self.api_client.delete(f"/notices/{notice_id}"),
            lambda _result: self._load(),
            lambda message: QMessageBox.critical(self, "Couldn't delete notice", message),
        )

    def _restore_selected(self):
        notice = self.selected
        if not notice:
            return
        notice_id = notice.get("noticeId")
        self._run(
            lambda: self.api_client.post(f"/notices/{notice_id}/restore"),
            lambda _result: self._load(),
            lambda message: QMessageBox.critical(self, "Couldn't restore notice", message),
        )
