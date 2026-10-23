"""Users > Worker Details (24 October 2026, migration 040).

"worker ID number and documents with admin/SuperAdmin verification". Workers
fill in their own details and photograph their documents in the phone app.
Here everyone sees where each worker stands; only the SuperAdmin opens the
numbers and photos ("SuperAdmin only"), and so only the SuperAdmin verifies,
sends back with a note, deletes ("keep until deleted by hand") and adds new
fields or document types ("this system can grow"). Every opening is logged by
the backend.
"""
import base64

from PySide6.QtCore import Qt, QThreadPool
from PySide6.QtGui import QPixmap
from PySide6.QtWidgets import (
    QCheckBox,
    QComboBox,
    QDialog,
    QFormLayout,
    QFrame,
    QGridLayout,
    QHBoxLayout,
    QInputDialog,
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QMessageBox,
    QPushButton,
    QScrollArea,
    QVBoxLayout,
    QWidget,
)

from app.core.api_client import ApiError, readable
from app.core.worker import Worker
from app.ui.widgets.data_table import DataTable

STATUS_WORDS = {
    "NOT_STARTED": "Not sent yet",
    "SUBMITTED": "Waiting for checking",
    "VERIFIED": "Verified",
    "NEEDS_CHANGES": "Needs changes",
}


def _run(owner, fn, on_done, on_error):
    worker = Worker(fn)
    worker.signals.finished.connect(on_done)
    worker.signals.error.connect(on_error)
    owner._active_workers.append(worker)
    QThreadPool.globalInstance().start(worker)


class WorkerDetailsPage(QWidget):
    def __init__(self, api_client, is_super_admin: bool, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.is_super_admin = is_super_admin
        self.selected: dict | None = None
        self._active_workers = []

        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        hint = QLabel(
            "Staff fill in their ID or passport number, KRA PIN, NSSF and SHA numbers, next of kin and document photos "
            "in the phone app (My profile > Worker details). "
            + ("Open someone to check and verify them." if is_super_admin else "Only the SuperAdmin can open them; here is where each one stands.")
        )
        hint.setProperty("role", "muted")
        hint.setWordWrap(True)
        layout.addWidget(hint)

        self.table = DataTable("Worker Details", show_limit_selector=False, searchable=True, search_placeholder="Search by name or username")
        self.table.hidden_columns = {"userId"}
        self.table.refresh_requested.connect(lambda _limit: self.load())
        self.table.row_selected.connect(self._on_selected)
        self.table.row_clicked.connect(self._on_selected)
        layout.addWidget(self.table, 1)

        actions = QHBoxLayout()
        self.selection = QLabel("Click a worker.")
        self.selection.setProperty("role", "muted")
        actions.addWidget(self.selection)
        actions.addStretch()
        self.fields_btn = QPushButton("Fields and Documents...")
        self.fields_btn.setVisible(is_super_admin)
        self.fields_btn.clicked.connect(self._open_definitions)
        actions.addWidget(self.fields_btn)
        self.open_btn = QPushButton("Open Details")
        self.open_btn.setProperty("role", "primary")
        self.open_btn.setEnabled(False)
        self.open_btn.setVisible(is_super_admin)
        self.open_btn.clicked.connect(self._open_record)
        actions.addWidget(self.open_btn)
        layout.addLayout(actions)

    def load(self):
        self.table.set_loading()
        _run(self, lambda: self.api_client.get("/workers"), self._show, lambda m: self.table.set_error(readable(m)))

    def _show(self, rows):
        shaped = [
            {
                "userId": r.get("userId"),
                "name": r.get("name"),
                "username": r.get("userName"),
                "role": r.get("roleName"),
                "status": STATUS_WORDS.get(r.get("workerStatus"), r.get("workerStatus")),
                "Details given": r.get("detailsGiven"),
                "Photos given": r.get("photosGiven"),
                "sent": str(r.get("submittedAt") or "")[:10],
                "verified": str(r.get("verifiedAt") or "")[:10],
                "note": r.get("statusNote") or "",
            }
            for r in rows or []
        ]
        self.table.set_rows(shaped)

    def _on_selected(self, row: dict):
        self.selected = row
        self.selection.setText(f"Selected: {row.get('name')} ({row.get('username')}) - {row.get('status')}")
        self.open_btn.setEnabled(self.is_super_admin)

    def _open_record(self):
        if not self.selected:
            return
        dialog = WorkerRecordDialog(self.api_client, self.selected.get("userId"), parent=self)
        dialog.exec()
        self.load()

    def _open_definitions(self):
        DefinitionsDialog(self.api_client, parent=self).exec()


class WorkerRecordDialog(QDialog):
    """One worker's details and photos, opened by the SuperAdmin (logged)."""

    def __init__(self, api_client, user_id: int, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.user_id = user_id
        self._active_workers = []
        self.setWindowTitle("Worker details")
        self.setMinimumWidth(640)

        self.card = QFrame()
        self.card.setProperty("role", "card")
        self.card_layout = QVBoxLayout(self.card)
        self.card_layout.setContentsMargins(22, 18, 22, 18)
        self.title = QLabel("Loading...")
        self.title.setProperty("role", "title")
        self.card_layout.addWidget(self.title)
        self.status = QLabel("")
        self.status.setWordWrap(True)
        self.card_layout.addWidget(self.status)
        self.grid = QGridLayout()
        self.grid.setHorizontalSpacing(18)
        self.grid.setVerticalSpacing(6)
        self.card_layout.addLayout(self.grid)
        self.docs_title = QLabel("Document photos")
        self.docs_title.setProperty("role", "title")
        self.card_layout.addWidget(self.docs_title)
        self.docs = QListWidget()
        self.docs.setMinimumHeight(190)
        self.docs.setMaximumHeight(230)
        self.docs.itemDoubleClicked.connect(lambda _item: self._view_document())
        self.card_layout.addWidget(self.docs)
        doc_row = QHBoxLayout()
        doc_row.addStretch()
        self.view_btn = QPushButton("View Photo")
        self.view_btn.clicked.connect(self._view_document)
        doc_row.addWidget(self.view_btn)
        self.delete_doc_btn = QPushButton("Delete Photo")
        self.delete_doc_btn.setProperty("role", "danger")
        self.delete_doc_btn.clicked.connect(self._delete_document)
        doc_row.addWidget(self.delete_doc_btn)
        self.card_layout.addLayout(doc_row)
        note = QLabel("Opening these is written to the audit log.")
        note.setProperty("role", "muted")
        self.card_layout.addWidget(note)

        self.message = QLabel("")
        self.message.setProperty("role", "error")
        self.message.setWordWrap(True)

        buttons = QHBoxLayout()
        delete_all = QPushButton("Delete All Details")
        delete_all.setProperty("role", "danger")
        delete_all.clicked.connect(self._delete_all)
        buttons.addWidget(delete_all)
        buttons.addStretch()
        send_back = QPushButton("Send Back...")
        send_back.clicked.connect(self._send_back)
        buttons.addWidget(send_back)
        verify = QPushButton("Mark Verified")
        verify.setProperty("role", "primary")
        verify.clicked.connect(lambda: self._verify("VERIFIED", None))
        buttons.addWidget(verify)
        close = QPushButton("Close")
        close.clicked.connect(self.accept)
        buttons.addWidget(close)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(18, 18, 18, 16)
        layout.addWidget(self.card)
        layout.addWidget(self.message)
        layout.addLayout(buttons)
        _run(self, lambda: self.api_client.get(f"/workers/{self.user_id}"), self._show, lambda m: self.message.setText(readable(m)))

    def _show(self, record: dict):
        record = record or {}
        user = record.get("user") or {}
        self.title.setText(f"{user.get('name')} (@{user.get('userName')}) - {user.get('roleName')}")
        words = STATUS_WORDS.get(record.get("status"), record.get("status"))
        extra = f" - {record.get('statusNote')}" if record.get("status") == "NEEDS_CHANGES" and record.get("statusNote") else ""
        self.status.setText(f"Status: {words}{extra}")
        while self.grid.count():
            item = self.grid.takeAt(0)
            if item.widget():
                item.widget().hide()
                item.widget().deleteLater()
        for row, field in enumerate(record.get("fields") or []):
            label = QLabel(field.get("label") + ("" if field.get("isActive") else " (no longer asked)"))
            label.setProperty("role", "muted")
            value = QLabel(field.get("value") or "-")
            value.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
            self.grid.addWidget(label, row, 0)
            self.grid.addWidget(value, row, 1)
        self.docs.clear()
        for doc in record.get("docTypes") or []:
            sent = doc.get("uploadedAt")
            text = f"{doc.get('label')}  -  " + (f"sent {str(sent)[:10]}" if sent else "not sent")
            item = QListWidgetItem(text)
            item.setData(Qt.ItemDataRole.UserRole, doc)
            self.docs.addItem(item)
        # The details arrive after the window opens - grow it to fit them.
        self.adjustSize()

    def _selected_doc(self) -> dict | None:
        item = self.docs.currentItem()
        doc = item.data(Qt.ItemDataRole.UserRole) if item else None
        return doc if doc and doc.get("documentId") else None

    def _view_document(self):
        doc = self._selected_doc()
        if doc is None:
            self.message.setText("Click a photo that has been sent.")
            return
        self.message.setText("")
        try:
            result = self.api_client.get(f"/workers/{self.user_id}/documents/{doc.get('documentId')}")
        except ApiError as exc:
            self.message.setText(readable(str(exc.detail)))
            return
        pixmap = QPixmap()
        pixmap.loadFromData(base64.b64decode(result.get("data") or ""))
        viewer = QDialog(self)
        viewer.setWindowTitle(doc.get("label") or "Document")
        area = QScrollArea()
        picture = QLabel()
        picture.setPixmap(pixmap.scaled(1000, 760, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation))
        area.setWidget(picture)
        box = QVBoxLayout(viewer)
        box.addWidget(area)
        viewer.resize(min(pixmap.width(), 1000) + 64, min(pixmap.height(), 760) + 64)
        viewer.exec()

    def _delete_document(self):
        doc = self._selected_doc()
        if doc is None:
            self.message.setText("Click a photo that has been sent.")
            return
        if QMessageBox.question(self, "Delete photo?", f"Delete the {doc.get('label')} photo? This can't be undone.") != QMessageBox.StandardButton.Yes:
            return
        try:
            self.api_client.delete(f"/workers/{self.user_id}/documents/{doc.get('documentId')}")
            self._show(self.api_client.get(f"/workers/{self.user_id}"))
        except ApiError as exc:
            self.message.setText(readable(str(exc.detail)))

    def _verify(self, status: str, note: str | None):
        try:
            self._show(self.api_client.post(f"/workers/{self.user_id}/verify", json={"status": status, "note": note}))
            self.message.setText("")
        except ApiError as exc:
            self.message.setText(readable(str(exc.detail)))

    def _send_back(self):
        note, ok = QInputDialog.getText(self, "Send back", "What needs changing? (the worker sees this)")
        if ok and note.strip():
            self._verify("NEEDS_CHANGES", note.strip())

    def _delete_all(self):
        question = "Delete every detail and photo kept for this worker? This can't be undone."
        if QMessageBox.question(self, "Delete all details?", question) != QMessageBox.StandardButton.Yes:
            return
        try:
            self.api_client.delete(f"/workers/{self.user_id}")
        except ApiError as exc:
            self.message.setText(readable(str(exc.detail)))
            return
        self.accept()


class DefinitionsDialog(QDialog):
    """The SuperAdmin's list of what workers are asked for - add a field or a
    document type, or stop asking for one (what was given is kept)."""

    def __init__(self, api_client, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.setWindowTitle("Fields and documents")
        self.setMinimumWidth(620)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(18, 18, 18, 16)

        card = QFrame()
        card.setProperty("role", "card")
        box = QVBoxLayout(card)
        box.setContentsMargins(22, 16, 22, 16)
        title = QLabel("What workers are asked for")
        title.setProperty("role", "title")
        box.addWidget(title)
        hint = QLabel("Untick to stop asking (what was already given is kept). Only switch on what there is a real reason to collect.")
        hint.setProperty("role", "muted")
        hint.setWordWrap(True)
        box.addWidget(hint)
        self.list = QListWidget()
        self.list.setMinimumHeight(340)
        self.list.itemChanged.connect(self._toggled)
        box.addWidget(self.list)

        add_row = QHBoxLayout()
        add_field = QPushButton("Add Field...")
        add_field.clicked.connect(self._add_field)
        add_row.addWidget(add_field)
        add_doc = QPushButton("Add Document...")
        add_doc.clicked.connect(self._add_doc)
        add_row.addWidget(add_doc)
        add_row.addStretch()
        box.addLayout(add_row)
        layout.addWidget(card)

        self.message = QLabel("")
        self.message.setProperty("role", "error")
        self.message.setWordWrap(True)
        layout.addWidget(self.message)
        close = QPushButton("Close")
        close.clicked.connect(self.accept)
        row = QHBoxLayout()
        row.addStretch()
        row.addWidget(close)
        layout.addLayout(row)
        self._filling = False
        self._fill(self.api_client.get("/workers/definitions"))

    def _fill(self, data: dict):
        self._filling = True
        self.list.clear()
        for field in (data or {}).get("fields") or []:
            words = [field.get("kind")]
            if field.get("isSensitive"):
                words.append("encrypted")
            if field.get("isRequired"):
                words.append("required")
            item = QListWidgetItem(f"Field: {field.get('label')}  ({', '.join(words)})")
            item.setFlags(item.flags() | Qt.ItemFlag.ItemIsUserCheckable)
            item.setCheckState(Qt.CheckState.Checked if field.get("isActive") else Qt.CheckState.Unchecked)
            item.setData(Qt.ItemDataRole.UserRole, ("fields", field.get("fieldKey")))
            self.list.addItem(item)
        for doc in (data or {}).get("docTypes") or []:
            item = QListWidgetItem(f"Document: {doc.get('label')}" + ("  (required)" if doc.get("isRequired") else ""))
            item.setFlags(item.flags() | Qt.ItemFlag.ItemIsUserCheckable)
            item.setCheckState(Qt.CheckState.Checked if doc.get("isActive") else Qt.CheckState.Unchecked)
            item.setData(Qt.ItemDataRole.UserRole, ("doc-types", doc.get("typeKey")))
            self.list.addItem(item)
        self._filling = False

    def _toggled(self, item: QListWidgetItem):
        if self._filling:
            return
        kind, key = item.data(Qt.ItemDataRole.UserRole)
        try:
            self._fill(self.api_client.patch(f"/workers/{kind}/{key}", json={"active": item.checkState() == Qt.CheckState.Checked}))
            self.message.setText("")
        except ApiError as exc:
            self.message.setText(readable(str(exc.detail)))

    def _add_field(self):
        dialog = QDialog(self)
        dialog.setWindowTitle("Add a field")
        form = QFormLayout(dialog)
        label = QLineEdit()
        kind = QComboBox()
        for text, value in (("Text", "text"), ("Number", "number"), ("Date", "date"), ("Phone number", "phone")):
            kind.addItem(text, value)
        hint = QLineEdit()
        sensitive = QCheckBox("Encrypt it (ID-type numbers)")
        required = QCheckBox("Required before sending for checking")
        form.addRow("Name", label)
        form.addRow("Kind", kind)
        form.addRow("Hint", hint)
        form.addRow("", sensitive)
        form.addRow("", required)
        save = QPushButton("Add")
        save.clicked.connect(dialog.accept)
        form.addRow("", save)
        if dialog.exec():
            try:
                self._fill(self.api_client.post("/workers/fields", json={
                    "label": label.text(), "kind": kind.currentData(), "hint": hint.text() or None,
                    "sensitive": sensitive.isChecked(), "required": required.isChecked(),
                }))
            except ApiError as exc:
                self.message.setText(readable(str(exc.detail)))

    def _add_doc(self):
        dialog = QDialog(self)
        dialog.setWindowTitle("Add a document")
        form = QFormLayout(dialog)
        label = QLineEdit()
        hint = QLineEdit()
        required = QCheckBox("Required before sending for checking")
        form.addRow("Name", label)
        form.addRow("Hint", hint)
        form.addRow("", required)
        save = QPushButton("Add")
        save.clicked.connect(dialog.accept)
        form.addRow("", save)
        if dialog.exec():
            try:
                self._fill(self.api_client.post("/workers/doc-types", json={
                    "label": label.text(), "hint": hint.text() or None, "required": required.isChecked(),
                }))
            except ApiError as exc:
                self.message.setText(readable(str(exc.detail)))

