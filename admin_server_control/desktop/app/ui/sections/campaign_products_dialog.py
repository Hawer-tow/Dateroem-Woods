from PySide6.QtCore import QThreadPool
from PySide6.QtWidgets import (
    QCheckBox,
    QDialog,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QPushButton,
    QScrollArea,
    QVBoxLayout,
    QWidget,
)

from app.core.worker import Worker
from app.ui.widgets.busy import set_busy


class CampaignProductsDialog(QDialog):
    """"Products in this campaign": tick the products a campaign promotes.
    The website's Featured page shows a campaign only while it is Active
    and has at least one product, and tags those products for customers.
    Saving replaces the whole set; products in the bin keep their links."""

    def __init__(self, api_client, campaign: dict, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.campaign_id = campaign.get("campaignId")
        self._active_workers = []
        self.checks: list[tuple[QCheckBox, int, str]] = []
        self.setWindowTitle(f"Products in {campaign.get('name') or 'this campaign'}")
        self.setMinimumSize(540, 520)

        layout = QVBoxLayout(self)
        intro = QLabel(
            "Tick every product this campaign promotes. Customers see the campaign on the website "
            "only while it is Active and has at least one product."
        )
        intro.setWordWrap(True)
        intro.setProperty("role", "muted")
        layout.addWidget(intro)

        self.search = QLineEdit()
        self.search.setPlaceholderText("Search products")
        self.search.textChanged.connect(self._filter)
        layout.addWidget(self.search)

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        holder = QWidget()
        self.check_layout = QVBoxLayout(holder)
        self.check_layout.addStretch()
        scroll.setWidget(holder)
        layout.addWidget(scroll, 1)

        self.count_label = QLabel("Loading products...")
        self.count_label.setProperty("role", "muted")
        layout.addWidget(self.count_label)

        self.error_label = QLabel("")
        self.error_label.setProperty("role", "error")
        self.error_label.setWordWrap(True)
        layout.addWidget(self.error_label)

        row = QHBoxLayout()
        row.addStretch()
        self.cancel_btn = QPushButton("Cancel")
        self.cancel_btn.clicked.connect(self.reject)
        row.addWidget(self.cancel_btn)
        self.save_btn = QPushButton("Save")
        self.save_btn.setProperty("role", "primary")
        self.save_btn.setEnabled(False)
        self.save_btn.clicked.connect(self._save)
        row.addWidget(self.save_btn)
        layout.addLayout(row)

        self._run(self._fetch, self._on_loaded, self._on_load_error)

    def _run(self, fn, on_done, on_error):
        worker = Worker(fn)
        worker.signals.finished.connect(on_done)
        worker.signals.error.connect(on_error)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)

    def _fetch(self):
        products = self.api_client.get("/products") or []
        linked = self.api_client.get(f"/marketing/campaigns/{self.campaign_id}/products") or []
        return products, linked

    def _on_loaded(self, result):
        products, linked = result
        linked = set(linked)
        for product in sorted(products, key=lambda p: (p.get("name") or p.get("type") or "").lower()):
            name = product.get("name") or product.get("type") or f"Product #{product.get('productId')}"
            price = product.get("price")
            details = " - ".join(
                part for part in (product.get("type"), f"KES {price:,.0f}" if price is not None else None) if part
            )
            check = QCheckBox(f"{name}   ({details})" if details else name)
            check.setChecked(product.get("productId") in linked)
            check.toggled.connect(self._update_count)
            self.check_layout.insertWidget(self.check_layout.count() - 1, check)
            self.checks.append((check, product.get("productId"), check.text().lower()))
        if products:
            self._update_count()
        else:
            self.count_label.setText("There are no products yet - add one in Products first.")
        self.save_btn.setEnabled(True)

    def _on_load_error(self, message: str):
        self.count_label.setText("")
        self.error_label.setText(f"Couldn't load the products: {message}")

    def _chosen_ids(self) -> list[int]:
        return [product_id for check, product_id, _ in self.checks if check.isChecked()]

    def _update_count(self, *_):
        chosen = len(self._chosen_ids())
        self.count_label.setText("None chosen" if chosen == 0 else f"{chosen} chosen")

    def _filter(self, text: str):
        needle = text.strip().lower()
        for check, _, label in self.checks:
            check.setVisible(not needle or needle in label)

    def _save(self):
        ids = self._chosen_ids()
        self.error_label.setText("")
        set_busy(self.save_btn, True, "Saving")
        self.cancel_btn.setEnabled(False)

        def done(_result):
            set_busy(self.save_btn, False)
            self.accept()

        def failed(message: str):
            set_busy(self.save_btn, False)
            self.cancel_btn.setEnabled(True)
            self.error_label.setText(message)

        self._run(
            lambda: self.api_client.put(
                f"/marketing/campaigns/{self.campaign_id}/products", json={"productIds": ids}, actor=True
            ),
            done,
            failed,
        )
