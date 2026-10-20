from PySide6.QtCore import Qt, QThreadPool
from PySide6.QtGui import QGuiApplication
from PySide6.QtWidgets import (
    QDialog,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QMessageBox,
    QPushButton,
    QStackedWidget,
    QVBoxLayout,
    QWidget,
)

from app.core.worker import Worker
from app.ui.sections.orders_page import OrdersPage
from app.ui.widgets.busy import set_busy
from app.ui.widgets.data_table import DataTable
from app.ui.widgets.payout_dialog import PayoutDialog


class InsightsSection(QWidget):
    """The first slice of "an ai feature that allows one to perform some
    of the simplifications of the jobs" - starting with the most concrete,
    lowest-risk pieces of that idea: plain read-only queries against data
    that already exists, no OCR/voice/external-AI involved. Same toggle
    pattern as Users/Farmers - each insight is its own page, one growing
    section instead of a new sidebar entry per insight.

    "you ask any unassigned transactions? it will provide them and when
    you click them it will lead you to being taken to the assign
    transaction" - double-click a row to see its closest-matching
    candidates and assign it right there.

    "check a certain farmer and provide info that this farmer tops on the
    list as one of the farmers whom you bought most trees from" - ranked
    by total trees supplied across every trip.

    "for the expense it can scan for the expense provide information on
    the most expensive expense and observe even from other trips ... more
    cost were incurred in the transport section compared to the other
    trip" - one row per (trip, category), ranked so the biggest cost
    centers surface first and the same category is easy to compare
    across trips by scanning for it.
    """

    def __init__(self, api_client, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        # See GenericListSection - keeps in-flight Workers from being
        # garbage-collected before their signals fire.
        self._active_workers = []

        layout = QVBoxLayout(self)

        toggle_row = QHBoxLayout()
        # "in the insights section place the tab orders so we organize
        # things well" - first, since deciding where pending orders go is
        # the most hands-on job in here.
        self.orders_toggle_btn = QPushButton("Orders")
        self.orders_toggle_btn.setProperty("role", "primary")
        self.orders_toggle_btn.clicked.connect(lambda: self._switch_view(0))
        toggle_row.addWidget(self.orders_toggle_btn)
        self.transactions_toggle_btn = QPushButton("Unassigned Transactions")
        self.transactions_toggle_btn.clicked.connect(lambda: self._switch_view(1))
        toggle_row.addWidget(self.transactions_toggle_btn)
        self.farmers_toggle_btn = QPushButton("Top Farmers")
        self.farmers_toggle_btn.clicked.connect(lambda: self._switch_view(2))
        toggle_row.addWidget(self.farmers_toggle_btn)
        self.expenses_toggle_btn = QPushButton("Expense Breakdown")
        self.expenses_toggle_btn.clicked.connect(lambda: self._switch_view(3))
        toggle_row.addWidget(self.expenses_toggle_btn)
        # "i think its also fine time we add stk push in the desktop ...
        # if its there remind me where exactly" - it wasn't there at all;
        # same call the mobile app's own StkPushScreen makes.
        self.stk_toggle_btn = QPushButton("STK Push")
        self.stk_toggle_btn.clicked.connect(lambda: self._switch_view(4))
        toggle_row.addWidget(self.stk_toggle_btn)
        # "after i drop the order in desktop where do i now send the money
        # ... is there that option in the desktop a refund area" - the
        # drop dialog's own "Refund Now" only ever covers the order you
        # just dropped, right then; this is where to come back to any
        # dropped order still owing its customer, same list the mobile
        # app's Transactions -> Refunds screen shows.
        self.refunds_toggle_btn = QPushButton("Refunds")
        self.refunds_toggle_btn.clicked.connect(lambda: self._switch_view(5))
        toggle_row.addWidget(self.refunds_toggle_btn)
        # "the payment went through as a real one so why does the database
        # not show the reference or transaction id ... in the desktop" -
        # every website payment with its M-Pesa receipt.
        self.payments_toggle_btn = QPushButton("Website Payments")
        self.payments_toggle_btn.clicked.connect(lambda: self._switch_view(6))
        toggle_row.addWidget(self.payments_toggle_btn)
        toggle_row.addStretch()
        layout.addLayout(toggle_row)

        self.stack = QStackedWidget()
        layout.addWidget(self.stack)

        self.orders_page = OrdersPage(api_client)
        self.stack.addWidget(self.orders_page)
        self.stack.addWidget(self._build_transactions_page())
        self.stack.addWidget(self._build_farmers_page())
        self.stack.addWidget(self._build_expenses_page())
        self.stack.addWidget(self._build_stk_push_page())
        self.stack.addWidget(self._build_refunds_page())
        self.stack.addWidget(self._build_payments_page())

        self._load_unassigned()
        self._load_top_farmers()
        self._load_expense_breakdown()
        self._load_refunds()
        self._load_payments()

    def _switch_view(self, index: int):
        self.stack.setCurrentIndex(index)
        buttons = (
            self.orders_toggle_btn, self.transactions_toggle_btn, self.farmers_toggle_btn,
            self.expenses_toggle_btn, self.stk_toggle_btn, self.refunds_toggle_btn, self.payments_toggle_btn,
        )
        for i, btn in enumerate(buttons):
            btn.setProperty("role", "primary" if index == i else "")
            btn.style().unpolish(btn)
            btn.style().polish(btn)

    def _run(self, fn, on_done, on_error):
        worker = Worker(fn)
        worker.signals.finished.connect(on_done)
        worker.signals.error.connect(on_error)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)

    # --- Unassigned Transactions -----------------------------------------

    def _build_transactions_page(self) -> QWidget:
        page = QWidget()
        page_layout = QVBoxLayout(page)
        page_layout.setContentsMargins(0, 0, 0, 0)

        hint = QLabel(
            "Financial transactions with no expense/loan/etc. assigned yet. "
            "Double-click one to see match candidates and assign it."
        )
        hint.setProperty("role", "muted")
        hint.setWordWrap(True)
        page_layout.addWidget(hint)

        self.transactions_table = DataTable("Unassigned Transactions", show_limit_selector=False, searchable=True, search_placeholder="Search by receipt, phone or amount")
        self.transactions_table.refresh_requested.connect(self._load_unassigned)
        self.transactions_table.row_selected.connect(self._open_assign)
        page_layout.addWidget(self.transactions_table)
        return page

    def _load_unassigned(self, _limit=None):
        self.transactions_table.set_loading()
        self._run(
            lambda: self.api_client.get("/transactions/unassigned"),
            lambda rows: self.transactions_table.set_rows(rows or []),
            self.transactions_table.set_error,
        )

    def _open_assign(self, transaction: dict):
        financial_transaction_id = transaction.get("financialTransactionId")

        dialog = QDialog(self)
        dialog.setWindowTitle(f"Assign Transaction #{financial_transaction_id} - KES {transaction.get('amount')}")
        dialog.resize(640, 420)
        dialog_layout = QVBoxLayout(dialog)

        hint = QLabel(
            "Closest-matching unassigned expense/loan holders, ranked by amount and timing. "
            "Double-click one to assign this transaction to it."
        )
        hint.setProperty("role", "muted")
        hint.setWordWrap(True)
        dialog_layout.addWidget(hint)

        candidates_table = DataTable("Candidates", show_limit_selector=False)
        dialog_layout.addWidget(candidates_table)
        candidates_table.refresh_requested.connect(lambda _limit: load_candidates())

        def load_candidates():
            candidates_table.set_loading()
            self._run(
                lambda: self.api_client.get(f"/transactions/{financial_transaction_id}/candidates"),
                lambda rows: candidates_table.set_rows(rows or []),
                candidates_table.set_error,
            )

        def on_assigned(_result):
            dialog.accept()
            self._load_unassigned()

        def do_assign(candidate: dict):
            target = candidate.get("target")
            target_row_id = candidate.get("rowId")
            if not target or target_row_id is None:
                return
            confirm = QMessageBox.question(
                dialog, "Assign transaction?",
                f"Assign transaction #{financial_transaction_id} to {target} #{target_row_id}?"
            )
            if confirm != QMessageBox.StandardButton.Yes:
                return
            self._run(
                lambda: self.api_client.post(
                    f"/transactions/{financial_transaction_id}/assign",
                    json={"target": target, "targetRowId": target_row_id},
                    actor=True,
                ),
                on_assigned,
                lambda msg: QMessageBox.critical(dialog, "Couldn't assign", msg),
            )

        candidates_table.row_selected.connect(do_assign)
        load_candidates()
        dialog.exec()

    # --- Top Farmers -------------------------------------------------

    def _build_farmers_page(self) -> QWidget:
        page = QWidget()
        page_layout = QVBoxLayout(page)
        page_layout.setContentsMargins(0, 0, 0, 0)

        hint = QLabel("Farmers ranked by total trees supplied, across every trip they've sold to.")
        hint.setProperty("role", "muted")
        hint.setWordWrap(True)
        page_layout.addWidget(hint)

        self.farmers_table = DataTable("Top Farmers", show_limit_selector=False)
        self.farmers_table.refresh_requested.connect(self._load_top_farmers)
        page_layout.addWidget(self.farmers_table)
        return page

    def _load_top_farmers(self, _limit=None):
        self.farmers_table.set_loading()
        self._run(
            lambda: self.api_client.get("/farmers/insights/top-suppliers"),
            lambda rows: self.farmers_table.set_rows(rows or []),
            self.farmers_table.set_error,
        )

    # --- Expense Breakdown -----------------------------------------------

    def _build_expenses_page(self) -> QWidget:
        page = QWidget()
        page_layout = QVBoxLayout(page)
        page_layout.setContentsMargins(0, 0, 0, 0)

        hint = QLabel(
            "Every trip's expenses, one row per category, ranked highest first - the biggest cost "
            "centers surface at the top, and the same category is easy to compare across trips."
        )
        hint.setProperty("role", "muted")
        hint.setWordWrap(True)
        page_layout.addWidget(hint)

        self.expenses_table = DataTable("Expense Breakdown", show_limit_selector=False)
        self.expenses_table.refresh_requested.connect(self._load_expense_breakdown)
        page_layout.addWidget(self.expenses_table)
        return page

    def _load_expense_breakdown(self, _limit=None):
        self.expenses_table.set_loading()
        self._run(
            lambda: self.api_client.get("/insights/expenses/breakdown"),
            lambda rows: self.expenses_table.set_rows(rows or []),
            self.expenses_table.set_error,
        )

    # --- STK Push ----------------------------------------------------
    # "the transaction section also have the stk push section and this
    # should also work for any user since it is mostly for bulk goods" -
    # STK pulls money FROM a customer (mainly a bulk-goods payment taken
    # over the desk), never pre-targeted to an expense category - that's
    # Send Money's job. Same two-step SEND -> CONFIRM shape as the mobile
    # app's StkPushViewModel: send the prompt, then confirm with whatever
    # M-Pesa receipt code the customer read out (there's no live Daraja
    # callback in this dev setup to auto-confirm it).

    def _build_stk_push_page(self) -> QWidget:
        page = QWidget()
        outer = QVBoxLayout(page)
        outer.setContentsMargins(0, 0, 0, 0)

        hint = QLabel("Prompts a customer's phone for an M-Pesa payment - anyone can send this.")
        hint.setProperty("role", "muted")
        hint.setWordWrap(True)
        hint.setAlignment(Qt.AlignmentFlag.AlignHCenter)
        outer.addWidget(hint)

        # "arrange the input areas well some stretch all over and its a
        # button center everything in the middle of the view so that it
        # does not feel invasive" - a plain QVBoxLayout stretches every
        # field/button to the full width of the panel; centering a
        # fixed-width card instead (same technique login_window.py's own
        # sign-in card uses) keeps it from sprawling across the screen.
        center_row = QHBoxLayout()
        center_row.addStretch(1)
        card = QWidget()
        # setMaximumWidth alone let the card collapse to its content's
        # tiny natural size (nothing was pushing it wider) - a fixed width
        # instead, the exact same one login_window.py's own sign-in card
        # uses (CARD_WIDTH), keeps it a consistent, comfortable size.
        card.setFixedWidth(420)
        form_layout = QVBoxLayout(card)
        form_layout.setContentsMargins(0, 16, 0, 0)
        form_layout.setSpacing(10)

        form_layout.addWidget(QLabel("Amount (KES) *"))
        self.stk_amount_input = QLineEdit()
        self.stk_amount_input.setProperty("role", "login-field")
        form_layout.addWidget(self.stk_amount_input)

        form_layout.addWidget(QLabel("Phone number"))
        self.stk_phone_input = QLineEdit()
        self.stk_phone_input.setProperty("role", "login-field")
        self.stk_phone_input.setPlaceholderText("2547XXXXXXXX or 07XXXXXXXX")
        form_layout.addWidget(self.stk_phone_input)

        self.stk_send_btn = QPushButton("Send Prompt")
        self.stk_send_btn.setProperty("role", "primary")
        self.stk_send_btn.clicked.connect(self._send_stk_push)
        form_layout.addWidget(self.stk_send_btn)

        self.stk_status_label = QLabel("")
        self.stk_status_label.setWordWrap(True)
        self.stk_status_label.setAlignment(Qt.AlignmentFlag.AlignHCenter)
        form_layout.addWidget(self.stk_status_label)

        form_layout.addWidget(QLabel("M-Pesa receipt code"))
        self.stk_receipt_input = QLineEdit()
        self.stk_receipt_input.setProperty("role", "login-field")
        form_layout.addWidget(self.stk_receipt_input)
        self.stk_confirm_btn = QPushButton("Confirm Payment")
        self.stk_confirm_btn.setProperty("role", "primary")
        self.stk_confirm_btn.setEnabled(False)
        self.stk_confirm_btn.clicked.connect(self._confirm_stk_push)
        form_layout.addWidget(self.stk_confirm_btn)

        self.stk_error_label = QLabel("")
        self.stk_error_label.setProperty("role", "error")
        self.stk_error_label.setWordWrap(True)
        self.stk_error_label.setAlignment(Qt.AlignmentFlag.AlignHCenter)
        form_layout.addWidget(self.stk_error_label)

        center_row.addWidget(card)
        center_row.addStretch(1)
        outer.addLayout(center_row)
        outer.addStretch(1)

        self._stk_financial_transaction_id = None
        return page

    def _send_stk_push(self):
        try:
            amount = float(self.stk_amount_input.text().strip())
        except ValueError:
            self.stk_error_label.setText("Enter a valid amount.")
            return
        phone = self.stk_phone_input.text().strip() or None
        self.stk_error_label.setText("")
        set_busy(self.stk_send_btn, True, "Sending")

        def done(response):
            set_busy(self.stk_send_btn, False)
            response = response or {}
            self._stk_financial_transaction_id = response.get("financialTransactionId")
            initiated = bool(response.get("darajaInitiated"))
            self.stk_status_label.setText(
                "Sent - check the phone for the M-Pesa prompt, then confirm here with the receipt code."
                if initiated else
                "Recorded (Daraja isn't configured/unreachable right now) - confirm with the receipt code once it's paid."
            )
            self.stk_confirm_btn.setEnabled(True)

        def failed(message: str):
            set_busy(self.stk_send_btn, False)
            self.stk_error_label.setText(message)

        self._run(
            lambda: self.api_client.post(
                "/transactions/stk-push", json={"amount": amount, "phoneNumber": phone}, actor=True
            ),
            done, failed,
        )

    def _confirm_stk_push(self):
        if self._stk_financial_transaction_id is None:
            return
        reference = self.stk_receipt_input.text().strip()
        if not reference:
            self.stk_error_label.setText("Enter the M-Pesa receipt code.")
            return
        self.stk_error_label.setText("")
        set_busy(self.stk_confirm_btn, True, "Confirming")

        def done(response):
            set_busy(self.stk_confirm_btn, False)
            status = (response or {}).get("status", "confirmed")
            self.stk_status_label.setText(f"Payment confirmed ({status}).")
            self.stk_confirm_btn.setEnabled(False)
            self.stk_receipt_input.clear()
            self.stk_amount_input.clear()
            self.stk_phone_input.clear()
            self._stk_financial_transaction_id = None

        def failed(message: str):
            set_busy(self.stk_confirm_btn, False)
            self.stk_error_label.setText(message)

        self._run(
            lambda: self.api_client.post(
                f"/transactions/{self._stk_financial_transaction_id}/stk-push/confirm",
                json={"referenceId": reference},
                actor=True,
            ),
            done, failed,
        )

    # --- Refunds -------------------------------------------------------
    # "after i drop the order in desktop where do i now send the money for
    # this dropped [order] ... is there that option in the desktop a
    # refund area" - every dropped order still owing its customer a
    # refund (dropOrder() only ever records the figure on the order
    # itself - it was never a candidate for Unassigned Transactions above,
    # that's for matching unclaimed INCOMING payments). Same list, same
    # Pay-out mechanics, as the mobile app's Refunds screen.

    def _build_refunds_page(self) -> QWidget:
        page = QWidget()
        page_layout = QVBoxLayout(page)
        page_layout.setContentsMargins(0, 0, 0, 0)

        hint = QLabel("Dropped orders still owing their customer a refund. Double-click one to pay it out.")
        hint.setProperty("role", "muted")
        hint.setWordWrap(True)
        page_layout.addWidget(hint)

        self.refunds_table = DataTable("Refunds", show_limit_selector=False, searchable=True, search_placeholder="Search by customer or phone")
        self.refunds_table.refresh_requested.connect(lambda _limit: self._load_refunds())
        self.refunds_table.row_selected.connect(self._open_refund)
        page_layout.addWidget(self.refunds_table)
        return page

    def _load_refunds(self, _limit=None):
        self.refunds_table.set_loading()
        self._run(
            lambda: self.api_client.get("/orders/refunds/pending"),
            lambda rows: self.refunds_table.set_rows(rows or []),
            self.refunds_table.set_error,
        )

    def _open_refund(self, refund: dict):
        order_id = refund.get("orderId")
        if order_id is None:
            return
        dialog = PayoutDialog(
            self.api_client,
            f"Refund Order #{order_id} - Send Money",
            purpose=f"Refund for Order #{order_id}",
            recipient_target="ORDER_REFUND",
            recipient_row_id=order_id,
            send_fn=lambda amount, phone: self.api_client.post(
                "/transactions/send-money",
                json={"amount": amount, "phoneNumber": phone, "target": "ORDER_REFUND", "targetRowId": order_id},
                actor=True,
                timeout=85,
            ),
            prefilled_amount=refund.get("refundedAmount"),
            parent=self,
        )
        dialog.exec()
        self._load_refunds()

    # --- Website Payments -------------------------------------------------
    # Every payment made on the website, newest first, with its M-Pesa
    # receipt (PRODUCT_PAYMENTS.Mpesa_Receipt, migration 035).

    def _build_payments_page(self) -> QWidget:
        page = QWidget()
        page_layout = QVBoxLayout(page)
        page_layout.setContentsMargins(0, 0, 0, 0)

        hint = QLabel(
            "Payments made on the website, newest first, with the M-Pesa receipt Safaricom sent. "
            "Double-click a row to copy its receipt."
        )
        hint.setProperty("role", "muted")
        hint.setWordWrap(True)
        page_layout.addWidget(hint)

        self.payments_table = DataTable(
            "Website Payments", show_limit_selector=False, searchable=True,
            search_placeholder="Search by receipt, customer or order number",
        )
        self.payments_table.refresh_requested.connect(lambda _limit: self._load_payments())
        self.payments_table.row_selected.connect(self._copy_receipt)
        page_layout.addWidget(self.payments_table)

        self.payments_status = QLabel("")
        self.payments_status.setProperty("role", "muted")
        page_layout.addWidget(self.payments_status)
        return page

    def _load_payments(self, _limit=None):
        self.payments_table.set_loading()
        self._run(
            lambda: self.api_client.get("/orders/payments", params={"limit": 500}),
            lambda rows: self.payments_table.set_rows([_payment_row(r) for r in rows or []]),
            self.payments_table.set_error,
        )

    def _copy_receipt(self, row: dict):
        receipt = row.get("M-Pesa receipt")
        if not receipt:
            self.payments_status.setText(f"Order #{row.get('order')} has no M-Pesa receipt to copy.")
            return
        QGuiApplication.clipboard().setText(receipt)
        self.payments_status.setText(f"Copied {receipt} (order #{row.get('order')}).")


def _payment_row(payment: dict) -> dict:
    """One Website Payments row, with plain column names."""
    return {
        "date": payment.get("createdAt"),
        "M-Pesa receipt": payment.get("mpesaReceipt") or "",
        "order": payment.get("orderId"),
        "customer": payment.get("customerName") or payment.get("userName") or "",
        "amount (KES)": payment.get("amount"),
        "payment": payment.get("status"),
        "order status": payment.get("orderStatus"),
        "note": payment.get("receiptNote") or "",
    }
