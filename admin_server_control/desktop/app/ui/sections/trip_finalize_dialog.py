"""Finalize a trip.

"you can never create a new trip before you finalize every detail in the
other trip - deliveries, loans ... all the orders during that trip should
either be delivered or, if that wasn't possible, dropped from those
deliveries and taken to the next trip ... all those must be finalized first."

Shows exactly what still stands in the way, and only lets the trip close once
it's clear: orders not delivered can be dropped back to the pending list
(flagged priority for the next trip); bulk deliveries must be finished or
deleted; loans must be paid or written off.
"""
from PySide6.QtCore import QThreadPool
from PySide6.QtWidgets import QCheckBox, QDialog, QHBoxLayout, QLabel, QPushButton, QVBoxLayout

from app.core.worker import Worker
from app.ui.widgets.busy import set_busy


class FinalizeTripDialog(QDialog):
    def __init__(self, api_client, trip_id: int, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.trip_id = trip_id
        self._active_workers = []
        self.setWindowTitle(f"Finalize Trip #{trip_id}")
        self.setMinimumWidth(560)

        layout = QVBoxLayout(self)
        self.status_label = QLabel("Checking what's left on this trip...")
        self.status_label.setWordWrap(True)
        layout.addWidget(self.status_label)

        self.drop_check = QCheckBox("")
        self.drop_check.setVisible(False)
        self.drop_check.toggled.connect(lambda _checked: self._refresh_buttons())
        layout.addWidget(self.drop_check)

        self.error_label = QLabel("")
        self.error_label.setProperty("role", "error")
        self.error_label.setWordWrap(True)
        layout.addWidget(self.error_label)

        row = QHBoxLayout()
        row.addStretch()
        self.cancel_btn = QPushButton("Cancel")
        self.cancel_btn.clicked.connect(self.reject)
        row.addWidget(self.cancel_btn)
        self.confirm_btn = QPushButton("Finalize Trip")
        self.confirm_btn.setProperty("role", "primary")
        self.confirm_btn.setEnabled(False)
        self.confirm_btn.clicked.connect(self._confirm)
        row.addWidget(self.confirm_btn)
        layout.addLayout(row)

        self.check: dict = {}
        self._run(
            lambda: self.api_client.get(f"/trips/{trip_id}/finalization"),
            self._show_check,
            lambda message: self.status_label.setText(f"Couldn't check the trip: {message}"),
        )

    def _run(self, fn, on_done, on_error):
        worker = Worker(fn)
        worker.signals.finished.connect(on_done)
        worker.signals.error.connect(on_error)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)

    def _show_check(self, check: dict):
        self.check = check or {}
        lines = []
        pending = self.check.get("pendingOrders") or []
        bulk = self.check.get("pendingBulkDeliveries") or []
        loans = self.check.get("activeLoans") or []

        if not self.check.get("open"):
            lines.append("This trip is already finalized.")
        else:
            lines.append(
                "Delivered: " + ("all orders on this trip are delivered." if not pending else f"{len(pending)} order(s) are NOT delivered yet.")
            )
            for o in pending[:8]:
                where = f"{o.get('deliveryCity') or ''} {o.get('areaName') or ''}".strip()
                lines.append(f"    - Order {o.get('ticketCode') or '#' + str(o.get('orderId'))}  KES {o.get('total')}  {where}")
            if len(pending) > 8:
                lines.append(f"    ... and {len(pending) - 8} more")
            lines.append("Bulk deliveries: " + ("all finished." if not bulk else f"{len(bulk)} not finished - finalize or delete them first."))
            for b in bulk:
                lines.append(f"    - Bulk delivery #{b.get('bulkDeliveryId')}  {b.get('deliveredTo') or ''}")
            lines.append("Loans: " + ("all settled." if not loans else f"{len(loans)} still ACTIVE - mark them Paid or Written off first."))
            for loan in loans:
                lines.append(f"    - Loan #{loan.get('loanId')}  KES {loan.get('amount')}  {loan.get('description') or ''}")
            lines.append("Profit: the trip's total payment and profit are recalculated and recorded when you finalize.")
        self.status_label.setText("\n".join(lines))

        if self.check.get("open") and pending:
            self.drop_check.setText(
                f"Drop the {len(pending)} undelivered order(s): they leave this trip and go back to the pending list, "
                "highlighted as priority for the next trip."
            )
            self.drop_check.setVisible(True)
        self._refresh_buttons()

    def _refresh_buttons(self):
        pending = self.check.get("pendingOrders") or []
        ok = bool(self.check.get("canFinalize")) and (not pending or self.drop_check.isChecked())
        self.confirm_btn.setEnabled(ok)

    def _confirm(self):
        set_busy(self.confirm_btn, True, "Finalizing")
        self.cancel_btn.setEnabled(False)
        drop = self.drop_check.isChecked()

        def done(_result):
            set_busy(self.confirm_btn, False)
            self.accept()

        def failed(message: str):
            set_busy(self.confirm_btn, False)
            self.cancel_btn.setEnabled(True)
            self.error_label.setText(message)

        self._run(
            lambda: self.api_client.post(f"/trips/{self.trip_id}/finalize", json={"dropUndelivered": drop}, actor=True),
            done,
            failed,
        )
