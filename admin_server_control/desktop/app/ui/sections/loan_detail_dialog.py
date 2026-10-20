"""One loan at a glance: how much was borrowed, how much has really been paid,
what's still waiting on M-Pesa, and what remains - with the repayments listed
and a button to pay the remainder straight from the company wallet.

"if you click on a loan you can see details on the loan e.g how much has been
paid and how much remains and from the remainder you can then initiate a send
money request".
"""
from PySide6.QtCore import QThreadPool
from PySide6.QtWidgets import QDialog, QHBoxLayout, QLabel, QPushButton, QVBoxLayout

from app.core.worker import Worker
from app.ui.widgets.data_table import DataTable
from app.ui.widgets.payout_dialog import PayoutDialog


def money(value) -> str:
    try:
        number = float(value or 0)
    except (TypeError, ValueError):
        return "0"
    return f"{number:,.0f}" if number == int(number) else f"{number:,.2f}"


class LoanDetailDialog(QDialog):
    def __init__(self, api_client, loan: dict, on_changed, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.loan = loan
        self.on_changed = on_changed
        self.loan_id = loan.get("loanId") or loan.get("id")
        self._active_workers = []
        self.setWindowTitle(f"Loan #{self.loan_id}")
        self.resize(620, 520)

        layout = QVBoxLayout(self)
        title = QLabel(loan.get("description") or f"Loan #{self.loan_id}")
        title.setProperty("role", "title")
        layout.addWidget(title)

        self.numbers_label = QLabel("")
        self.numbers_label.setWordWrap(True)
        layout.addWidget(self.numbers_label)

        self.repayments_table = DataTable("Repayments", show_limit_selector=False)
        self.repayments_table.refresh_requested.connect(lambda _limit: self.reload())
        layout.addWidget(self.repayments_table, 1)

        row = QHBoxLayout()
        row.addStretch()
        close = QPushButton("Close")
        close.clicked.connect(self.accept)
        row.addWidget(close)
        self.pay_btn = QPushButton("Pay Remainder (Send Money)")
        self.pay_btn.setProperty("role", "primary")
        self.pay_btn.clicked.connect(self._pay_remainder)
        row.addWidget(self.pay_btn)
        layout.addLayout(row)

        self._show_numbers()
        self.reload()

    def _run(self, fn, on_done, on_error):
        worker = Worker(fn)
        worker.signals.finished.connect(on_done)
        worker.signals.error.connect(on_error)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)

    def _show_numbers(self):
        loan = self.loan
        remaining = float(loan.get("remaining") or 0)
        pending = float(loan.get("pendingTotal") or 0)
        lines = [
            f"Borrowed: KES {money(loan.get('amount'))}     Paid: KES {money(loan.get('paidTotal'))}     "
            f"Remainder: KES {money(remaining)}",
        ]
        if pending > 0:
            lines.append(f"KES {money(pending)} more is on its way (waiting for M-Pesa to confirm) and isn't counted as paid yet.")
        lines.append("Status: " + str(loan.get("status") or "-"))
        self.numbers_label.setText("\n".join(lines))
        self.pay_btn.setEnabled(remaining > 0 and pending <= 0)
        self.pay_btn.setToolTip("" if remaining > 0 else "Nothing left to pay on this loan.")

    def reload(self):
        # Fresh figures for this loan (the list carries paid/remaining), then its repayments.
        trip_id = self.loan.get("tripId")

        def work():
            loans = self.api_client.get(f"/trips/{trip_id}/finance/loans") if trip_id else []
            repayments = self.api_client.get(f"/loans/{self.loan_id}/repayments")
            return loans, repayments

        def done(result):
            loans, repayments = result
            fresh = next((l for l in loans or [] if (l.get("loanId") or l.get("id")) == self.loan_id), None)
            if fresh:
                self.loan = fresh
            self._show_numbers()
            self.repayments_table.set_rows(repayments or [])

        self._run(work, done, self.repayments_table.set_error)

    def _pay_remainder(self):
        remaining = float(self.loan.get("remaining") or 0)
        dialog = PayoutDialog(
            self.api_client,
            f"Pay Loan #{self.loan_id} - Send Money",
            purpose=f"Loan #{self.loan_id}",
            recipient_target="LOAN",
            recipient_row_id=self.loan_id,
            send_fn=lambda amount, phone: self.api_client.post(
                f"/loans/{self.loan_id}/repayments/pay", json={"amount": amount, "phoneNumber": phone}, actor=True, timeout=85
            ),
            prefilled_amount=remaining,
            parent=self,
        )
        dialog.exec()
        self.reload()
        self.on_changed()
