from PySide6.QtCore import QThreadPool
from PySide6.QtWidgets import QCheckBox, QHBoxLayout, QLabel, QMessageBox, QPushButton, QVBoxLayout, QWidget

from app.core.worker import Worker
from app.ui.widgets.data_table import DataTable
from app.ui.widgets.record_form_dialog import FieldSpec, RecordFormDialog
from app.ui.sections.campaign_products_dialog import CampaignProductsDialog
from app.ui.sections.showcase_dialog import ShowcaseDialog

SHOWCASE_EDITORS = {"Marketer", "Admin", "SuperAdmin"}

FIELDS = [
    FieldSpec("name", "Name", required=True),
    FieldSpec("channel", "Channel", placeholder="e.g. TikTok, SMS, In-store"),
    FieldSpec("startAt", "Start date", type="date"),
    FieldSpec("endAt", "End date", type="date"),
    FieldSpec(
        "status", "Status", type="combo",
        options=[("Draft", "Draft"), ("Active", "Active"), ("Paused", "Paused"), ("Completed", "Completed")],
    ),
]


class MarketingSection(QWidget):
    """Full CRUD for marketing Campaigns - same shape as ProductsSection:
    view (with Deleted toggle), create, edit, delete/restore."""

    def __init__(self, api_client, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.selected: dict | None = None
        self.showing_deleted = False
        self._active_workers = []

        layout = QVBoxLayout(self)

        self.table = DataTable("Campaigns", searchable=True, search_placeholder="Search campaigns by name, channel or status")
        self.table.refresh_requested.connect(self._load)
        self.table.row_selected.connect(self._on_row_selected)
        layout.addWidget(self.table)

        action_row = QHBoxLayout()
        self.selection_label = QLabel("Double-click a campaign to select it.")
        self.selection_label.setProperty("role", "muted")
        action_row.addWidget(self.selection_label)
        action_row.addStretch()

        # The Bulk Orders page's example pictures - not a campaign, but kept
        # with the other things customers see. Marketers, Admins and the
        # SuperAdmin may change them.
        self.showcase_btn = QPushButton("Bulk order pictures...")
        self.showcase_btn.setToolTip("The slideshow on the website's Bulk Orders page")
        self.showcase_btn.clicked.connect(lambda: ShowcaseDialog(self.api_client, parent=self).exec())
        self.showcase_btn.setVisible(api_client.session.role_name in SHOWCASE_EDITORS)
        action_row.addWidget(self.showcase_btn)
        action_row.addSpacing(20)

        self.deleted_toggle = QCheckBox("Show deleted")
        self.deleted_toggle.toggled.connect(self._on_toggle_deleted)
        action_row.addWidget(self.deleted_toggle)

        self.new_btn = QPushButton("New Campaign")
        self.new_btn.setProperty("role", "primary")
        self.new_btn.clicked.connect(self._open_create)
        action_row.addWidget(self.new_btn)

        self.edit_btn = QPushButton("Edit")
        self.edit_btn.setEnabled(False)
        self.edit_btn.clicked.connect(self._open_edit)
        action_row.addWidget(self.edit_btn)

        # Which products the campaign promotes - the website shows a
        # campaign only once it is Active and has at least one.
        self.products_btn = QPushButton("Products...")
        self.products_btn.setEnabled(False)
        self.products_btn.clicked.connect(self._open_products)
        action_row.addWidget(self.products_btn)

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

    def _fields(self):
        # "in campaign only one of the medias can be used ... you can change
        # from one media to another" - one slot, a photo or a video, chosen
        # on this computer and uploaded on Save; its kind (mediaType) comes
        # from the file, so there is nothing to type or pick by hand.
        media = FieldSpec(
            "media", "Photo or video", type="media", allow_video=True, type_key="mediaType", upload=self._upload_media
        )
        return FIELDS + [media]

    def _upload_media(self, pending, values):
        body, content_type = pending.upload_body()
        params = {"name": values.get("name") or "campaign", "kind": pending.kind}
        if pending.kind == "video":
            params["extension"] = pending.extension
        return self.api_client.post_file("/uploads/campaigns", body, content_type, params=params)

    def _run(self, fn, on_done, on_error):
        worker = Worker(fn)
        worker.signals.finished.connect(on_done)
        worker.signals.error.connect(on_error)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)

    def _load(self, limit):
        self.table.set_loading()
        path = "/marketing/campaigns/deleted" if self.showing_deleted else "/marketing/campaigns"
        params = None if self.showing_deleted else ({"limit": limit} if limit else None)
        self._run(
            lambda: self.api_client.get(path, params=params),
            lambda rows: self.table.set_rows(rows or []),
            self.table.set_error,
        )

    def _on_toggle_deleted(self, checked: bool):
        self.showing_deleted = checked
        self.delete_btn.setVisible(not checked)
        self.restore_btn.setVisible(checked)
        self.selected = None
        self._update_action_state()
        self._load(self.table.current_limit())

    def _on_row_selected(self, row: dict):
        self.selected = row
        self.selection_label.setText(f"Selected: {row.get('name')} (#{row.get('campaignId')})")
        self._update_action_state()

    def _update_action_state(self):
        has_selection = self.selected is not None
        self.edit_btn.setEnabled(has_selection and not self.showing_deleted)
        self.products_btn.setEnabled(has_selection and not self.showing_deleted)
        self.delete_btn.setEnabled(has_selection and not self.showing_deleted)
        self.restore_btn.setEnabled(has_selection and self.showing_deleted)

    def _open_create(self):
        created = {}

        def save(values):
            created.update(self.api_client.post("/marketing/campaigns", json=values, actor=True) or {})
            return created

        dialog = RecordFormDialog("New Campaign", self._fields(), save_fn=save, parent=self)
        if dialog.exec():
            self._load(self.table.current_limit())
            if created.get("campaignId"):
                answer = QMessageBox.question(
                    self,
                    "Choose its products?",
                    f"Choose the products '{created.get('name')}' promotes now?\n\n"
                    "Customers only see a campaign on the website once it is Active and has at least one product.",
                )
                if answer == QMessageBox.StandardButton.Yes:
                    CampaignProductsDialog(self.api_client, created, parent=self).exec()

    def _open_products(self):
        if self.selected:
            CampaignProductsDialog(self.api_client, self.selected, parent=self).exec()

    def _open_edit(self):
        if not self.selected:
            return
        campaign_id = self.selected.get("campaignId")
        dialog = RecordFormDialog(
            f"Edit Campaign #{campaign_id}",
            self._fields(),
            save_fn=lambda values: self.api_client.patch(
                f"/marketing/campaigns/{campaign_id}", json=values, actor=True
            ),
            initial_values=self.selected,
            parent=self,
        )
        if dialog.exec():
            self._load(self.table.current_limit())

    def _delete_selected(self):
        if not self.selected:
            return
        campaign_id = self.selected.get("campaignId")
        name = self.selected.get("name")
        confirm = QMessageBox.question(self, "Delete campaign?", f"Delete '{name}'? This is a soft delete - it can be restored.")
        if confirm != QMessageBox.StandardButton.Yes:
            return

        def on_done(_result):
            self.selected = None
            self._update_action_state()
            self._load(self.table.current_limit())

        self._run(
            lambda: self.api_client.delete(f"/marketing/campaigns/{campaign_id}", actor=True),
            on_done,
            lambda msg: QMessageBox.critical(self, "Couldn't delete campaign", msg),
        )

    def _restore_selected(self):
        if not self.selected:
            return
        campaign_id = self.selected.get("campaignId")

        def on_done(_result):
            self.selected = None
            self._update_action_state()
            self._load(self.table.current_limit())

        self._run(
            lambda: self.api_client.post(f"/marketing/campaigns/{campaign_id}/restore", actor=True),
            on_done,
            lambda msg: QMessageBox.critical(self, "Couldn't restore campaign", msg),
        )
