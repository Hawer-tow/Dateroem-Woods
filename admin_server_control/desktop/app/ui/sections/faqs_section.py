from PySide6.QtCore import QThreadPool
from PySide6.QtWidgets import QCheckBox, QHBoxLayout, QLabel, QMessageBox, QPushButton, QVBoxLayout, QWidget

from app.core.worker import Worker
from app.ui.widgets.data_table import DataTable
from app.ui.widgets.record_form_dialog import FieldSpec, RecordFormDialog

# The website groups questions under these, in the order of their questions.
TOPICS = ["Ordering", "Payment", "Delivery and pickup", "Account and sign-in", "Ratings", "Offers", "About Dateroem", "Other"]
# Pages a question can send the customer to, named as in the website's menu.
PAGES = [
    ("(no link)", None),
    ("Products", "/"),
    ("Featured", "/featured"),
    ("Cart", "/cart"),
    ("Ordered Goods", "/my-orders"),
    ("Shipping", "/shipping"),
    ("Bulk Orders", "/heavy-order"),
    ("About", "/about"),
]
PAGE_NAMES = {path: name for name, path in PAGES if path}


class FaqsSection(QWidget):
    """"add this faqs in the desktop for editing adding new ones so that it
    constantly maintained based on changes done on certain features" - the
    questions on the website's Help page: add, edit, hide (untick "Show on
    the website"), delete and restore. Same layout as Products."""

    def __init__(self, api_client, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.faqs: dict[int, dict] = {}
        self.selected: dict | None = None
        self.showing_deleted = False
        self._active_workers = []

        layout = QVBoxLayout(self)

        intro = QLabel(
            "These questions show on the website's Help page, grouped by topic, smallest Order first. When a feature "
            "changes, update its answer here. An answer can have several lines; lines starting \"1. \", \"2. \" ... "
            "show as numbered steps."
        )
        intro.setWordWrap(True)
        intro.setProperty("role", "muted")
        layout.addWidget(intro)

        self.table = DataTable(
            "FAQs", show_limit_selector=False, searchable=True, search_placeholder="Search questions, answers or topics"
        )
        self.table.refresh_requested.connect(self._load)
        self.table.row_selected.connect(self._on_row_selected)
        layout.addWidget(self.table)

        action_row = QHBoxLayout()
        self.selection_label = QLabel("Double-click a question to select it.")
        self.selection_label.setProperty("role", "muted")
        action_row.addWidget(self.selection_label)
        action_row.addStretch()

        self.deleted_toggle = QCheckBox("Show deleted")
        self.deleted_toggle.toggled.connect(self._on_toggle_deleted)
        action_row.addWidget(self.deleted_toggle)

        self.new_btn = QPushButton("New Question")
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
        self._run(lambda: self.api_client.get("/faqs", params=params), self._on_loaded, self.table.set_error)

    def _on_loaded(self, faqs):
        faqs = faqs or []
        self.faqs = {faq.get("faqId"): faq for faq in faqs}
        rows = [
            {
                "#": faq.get("faqId"),
                "Topic": faq.get("topic"),
                "Question": faq.get("question"),
                "On the website": "Shown" if faq.get("isPublished") else "Hidden",
                "Order": faq.get("sortOrder"),
                "Links to": PAGE_NAMES.get(faq.get("linkPath"), faq.get("linkPath") or ""),
                "Last changed": str(faq.get("updatedAt") or faq.get("createdAt") or "").replace("T", " ")[:16],
                "By": faq.get("updatedByName") or "",
            }
            for faq in faqs
        ]
        self.table.set_rows(rows)
        self.selected = None
        self.selection_label.setText("Double-click a question to select it.")
        self._update_action_state()

    def _on_toggle_deleted(self, checked: bool):
        self.showing_deleted = checked
        self.delete_btn.setVisible(not checked)
        self.restore_btn.setVisible(checked)
        self.new_btn.setEnabled(not checked)
        self._load()

    def _on_row_selected(self, row: dict):
        self.selected = self.faqs.get(row.get("#"))
        if self.selected:
            self.selection_label.setText(f"Selected: {self.selected.get('question')}")
        self._update_action_state()

    def _update_action_state(self):
        has_selection = self.selected is not None
        self.edit_btn.setEnabled(has_selection and not self.showing_deleted)
        self.delete_btn.setEnabled(has_selection and not self.showing_deleted)
        self.restore_btn.setEnabled(has_selection and self.showing_deleted)

    def _fields(self, current_topic: str | None = None):
        topics = TOPICS if not current_topic or current_topic in TOPICS else [current_topic] + TOPICS
        return [
            FieldSpec("topic", "Topic", type="combo", required=True, options=[(t, t) for t in topics]),
            FieldSpec("question", "Question", required=True, placeholder="e.g. How long does delivery take?"),
            FieldSpec(
                "answer", "Answer", type="textarea", required=True, height=170,
                placeholder='Write it the way a customer would want to read it. Lines starting "1. " become numbered steps.',
            ),
            FieldSpec("linkPath", "Button to a page", type="combo", options=PAGES),
            FieldSpec("sortOrder", "Order (smaller shows first)", type="int", required=True, maximum=100_000),
            FieldSpec("published", "Show on the website", type="checkbox"),
        ]

    def _open_create(self):
        next_order = max((faq.get("sortOrder") or 0 for faq in self.faqs.values()), default=0) + 10
        dialog = RecordFormDialog(
            "New Question",
            self._fields(),
            save_fn=lambda values: self.api_client.post("/faqs", json=values),
            initial_values={"sortOrder": next_order, "published": True},
            parent=self,
        )
        if dialog.exec():
            self._load()

    def _open_edit(self):
        faq = self.selected
        if not faq:
            return
        faq_id = faq.get("faqId")
        dialog = RecordFormDialog(
            f"Edit Question #{faq_id}",
            self._fields(faq.get("topic")),
            save_fn=lambda values: self.api_client.patch(f"/faqs/{faq_id}", json=values),
            initial_values={**faq, "published": bool(faq.get("isPublished"))},
            parent=self,
        )
        if dialog.exec():
            self._load()

    def _delete_selected(self):
        faq = self.selected
        if not faq:
            return
        question = (
            f"Delete \"{faq.get('question')}\"?\n\nIt disappears from the website; it can be restored from Show deleted. "
            "To take it off the website for a while instead, Edit it and untick \"Show on the website\"."
        )
        if QMessageBox.question(self, "Delete question?", question) != QMessageBox.StandardButton.Yes:
            return
        faq_id = faq.get("faqId")
        self._run(
            lambda: self.api_client.delete(f"/faqs/{faq_id}"),
            lambda _result: self._load(),
            lambda message: QMessageBox.critical(self, "Couldn't delete question", message),
        )

    def _restore_selected(self):
        faq = self.selected
        if not faq:
            return
        faq_id = faq.get("faqId")
        self._run(
            lambda: self.api_client.post(f"/faqs/{faq_id}/restore"),
            lambda _result: self._load(),
            lambda message: QMessageBox.critical(self, "Couldn't restore question", message),
        )
