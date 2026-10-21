"""Send Money / Pay Loan with the receipt filling itself in.

"when you pay it it asks for a receipt isnt this supposed to fill itself
since send money is from the company wallet" - the amount and recipient are
looked up from the entry, "Send" fires the payout, and this dialog then
WATCHES it: M-Pesa's own result fills in the receipt, and the person gets a
success (or failure) message. Nothing to type.
"""
import time
from html import escape
from typing import Callable

from PySide6.QtCore import Qt, QThreadPool, QTimer
from PySide6.QtWidgets import (
    QCheckBox,
    QDialog,
    QFormLayout,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QProgressBar,
    QPushButton,
    QVBoxLayout,
    QWidget,
)

from app.core.worker import Worker
from app.ui import theme
from app.ui.widgets.busy import set_busy
from app.ui.widgets.transfer_scene import TransferScene

WATCH_SECONDS = 600  # the sandbox can take ~5 minutes to answer
POLL_SECONDS = 3


class ConfirmPaymentDialog(QDialog):
    """The security check before any money leaves: every detail in bold, and
    the payment stays locked until the person ticks that they are correct."""

    def __init__(self, amount: str, recipient: str | None, phone: str, purpose: str, sandbox: bool, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Confirm payment")
        self.setMinimumWidth(480)
        layout = QVBoxLayout(self)
        layout.setSpacing(8)

        title = QLabel("Confirm before sending")
        title.setStyleSheet("color: #4FA35C; font-size: 18px; font-weight: 700;")
        layout.addWidget(title)
        note = QLabel("For security, check every detail below. Money sent to the wrong number cannot be brought back.")
        note.setProperty("role", "muted")
        note.setWordWrap(True)
        layout.addWidget(note)

        def line(label: str, value: str, big: bool = False):
            box = QLabel(
                f"<span style='color:#8a9a8f; font-size:11px;'>{label}</span><br>"
                f"<span style='font-weight:800; font-size:{'22px' if big else '15px'};'>{escape(value)}</span>"
            )
            box.setTextFormat(Qt.TextFormat.RichText)
            layout.addWidget(box)

        line("Amount", f"KES {amount}", big=True)
        line("Send to", recipient or "Recipient not named")
        line("Phone number", phone)
        line("For", purpose)
        line("Paid from", "Company M-Pesa wallet")
        if sandbox:
            sb = QLabel("Sandbox mode: no real money moves.")
            sb.setProperty("role", "muted")
            layout.addWidget(sb)

        self.check = QCheckBox("I have checked the details and they are correct.")
        self.check.toggled.connect(lambda on: self.confirm_btn.setEnabled(on))
        layout.addWidget(self.check)

        row = QHBoxLayout()
        row.addStretch()
        back = QPushButton("Go back")
        back.clicked.connect(self.reject)
        row.addWidget(back)
        self.confirm_btn = QPushButton("Confirm && Send")
        # Money green in every theme; while it waits (a short countdown), a
        # quiet greyed button in the theme's own shade - it used to be a fixed
        # dark green, out of place in the Light theme.
        self.confirm_btn.setStyleSheet(
            "QPushButton { background: #4FA35C; color: white; font-weight: 700; border-radius: 6px; padding: 8px 16px; }"
            f"QPushButton:disabled {{ background: rgba({theme.INK}, 0.08); color: {theme.TEXT_MUTED}; }}"
        )
        self.confirm_btn.setEnabled(False)
        self.confirm_btn.clicked.connect(self.accept)
        row.addWidget(self.confirm_btn)
        layout.addLayout(row)

    def showEvent(self, event):
        super().showEvent(event)
        parent = self.parentWidget()
        if parent is not None:
            frame = self.frameGeometry()
            frame.moveCenter(parent.window().frameGeometry().center())
            self.move(frame.topLeft())


class PayoutDialog(QDialog):
    def __init__(
        self,
        api_client,
        title: str,
        purpose: str,
        recipient_target: str,
        recipient_row_id: int,
        send_fn: Callable[[float, str], dict],
        prefilled_amount=None,
        parent=None,
    ):
        super().__init__(parent)
        self.api_client = api_client
        self.purpose = purpose
        self.send_fn = send_fn
        self.recipient_name: str | None = None
        self.sandbox = False
        self.paid = False
        self._waited = 0
        self._active_workers = []
        self.setWindowTitle(title)
        # Big enough that the whole transfer - every stage and the receipt at
        # the end - is visible without scrolling or resizing.
        self.setMinimumSize(600, 560)
        self.resize(620, 580)

        layout = QVBoxLayout(self)
        self.info_label = QLabel("Looking up who this pays...")
        self.info_label.setProperty("role", "muted")
        self.info_label.setWordWrap(True)
        layout.addWidget(self.info_label)

        self.form_box = QWidget()
        form = QFormLayout(self.form_box)
        form.setContentsMargins(0, 0, 0, 0)
        self.amount_input = QLineEdit("" if prefilled_amount is None else self._fmt(prefilled_amount))
        self.phone_input = QLineEdit()
        # "the phone number came empty after some time it filled if one is
        # in a rush one may think it doesnt fill the number" - the lookup
        # is a background call, so there's a real (if brief) window before
        # it resolves; disabled + a placeholder that says so reads as
        # "loading", not "broken/empty", and stops a rushed click on Send
        # before the real number has actually landed.
        self.phone_input.setPlaceholderText("Looking up the recipient's number...")
        self.phone_input.setEnabled(False)
        form.addRow("Amount (KES) *", self.amount_input)
        form.addRow("Recipient phone *", self.phone_input)
        layout.addWidget(self.form_box)

        # While a payment is on its way this replaces the form: the payment
        # should look like the serious thing it is, not a spinner.
        self.scene = TransferScene()
        layout.addWidget(self.scene)

        self.bar = QProgressBar()
        self.bar.setRange(0, 0)
        self.bar.setTextVisible(False)
        self.bar.setFixedHeight(6)
        self.bar.hide()
        layout.addWidget(self.bar)

        self.message_label = QLabel("")
        self.message_label.setWordWrap(True)
        layout.addWidget(self.message_label)
        self.wait_label = QLabel("")
        self.wait_label.setProperty("role", "muted")
        layout.addWidget(self.wait_label)

        row = QHBoxLayout()
        row.addStretch()
        self.close_btn = QPushButton("Cancel")
        self.close_btn.clicked.connect(self.reject)
        row.addWidget(self.close_btn)
        self.send_btn = QPushButton("Send Money")
        self.send_btn.setProperty("role", "primary")
        self.send_btn.setEnabled(False)
        self.send_btn.clicked.connect(self._send)
        row.addWidget(self.send_btn)
        layout.addLayout(row)

        self._ticker = QTimer(self)
        self._ticker.timeout.connect(self._tick)

        self._run(
            lambda: api_client.get(
                "/transactions/send-money/recipient", params={"target": recipient_target, "row_id": recipient_row_id}
            ),
            self._on_recipient,
            self._on_recipient_lookup_failed,
        )

    def _unlock_form(self):
        self.phone_input.setEnabled(True)
        self.phone_input.setPlaceholderText("2547XXXXXXXX or 07XXXXXXXX")
        self.send_btn.setEnabled(True)

    def _on_recipient_lookup_failed(self, _message):
        self._unlock_form()
        self.info_label.setText("Fill in the amount and the recipient's phone number.")

    def showEvent(self, event):
        super().showEvent(event)
        self._center_on_parent()

    def _center_on_parent(self):
        # Sit in the middle of the trip window behind it so it feels like a
        # part of it rather than a separate window somewhere on the screen.
        parent = self.parentWidget()
        if parent is None:
            return
        host = parent.window().frameGeometry()
        frame = self.frameGeometry()
        frame.moveCenter(host.center())
        self.move(frame.topLeft())

    @staticmethod
    def _fmt(value) -> str:
        value = float(value)
        return str(int(value)) if value == int(value) else str(value)

    def _run(self, fn, on_done, on_error):
        worker = Worker(fn)
        worker.signals.finished.connect(on_done)
        worker.signals.error.connect(on_error)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)

    def _on_recipient(self, info):
        self._unlock_form()
        info = info or {}
        self.recipient_name = info.get("recipientName")
        self.sandbox = bool(info.get("sandbox"))
        if info.get("recipientPhone") and not self.phone_input.text():
            self.phone_input.setText(str(info["recipientPhone"]))
        if not self.amount_input.text() and info.get("amount") is not None:
            self.amount_input.setText(self._fmt(info["amount"]))
        who = f"{self.recipient_name} - " if self.recipient_name else ""
        line = f"Paying {self.purpose}. {who}from the company M-Pesa wallet. Amount and number can be changed."
        if self.sandbox:
            line += "\nSandbox mode: M-Pesa's test environment is in use, so no real money moves."
        self.info_label.setText(line)

    # ---- sending -----------------------------------------------------

    def _set_form_enabled(self, enabled: bool):
        self.amount_input.setEnabled(enabled)
        self.phone_input.setEnabled(enabled)

    def _send(self):
        try:
            amount = float(self.amount_input.text().strip())
        except ValueError:
            amount = 0
        phone = self.phone_input.text().strip()
        if amount <= 0:
            self._show("Enter a valid amount.", error=True)
            return
        if not phone:
            self._show("Enter the recipient's phone number.", error=True)
            return
        self._show("")
        if not self._confirmed(amount, phone):
            return
        self._sent_amount = amount
        set_busy(self.send_btn, True, "Sending")
        self._set_form_enabled(False)
        self.close_btn.setEnabled(False)
        self._show_scene(True)
        self.scene.set_sending(self._fmt(amount), self.recipient_name, stage=0)
        self._run(lambda: self.send_fn(amount, phone), self._on_sent, self._on_send_error)

    def _confirmed(self, amount: float, phone: str) -> bool:
        confirm = ConfirmPaymentDialog(self._fmt(amount), self.recipient_name, phone, self.purpose, self.sandbox, parent=self)
        return confirm.exec() == QDialog.DialogCode.Accepted

    def _show_scene(self, on: bool):
        self.form_box.setVisible(not on)
        self.info_label.setVisible(not on)
        if not on:
            self.scene.stop()

    def _on_send_error(self, message: str):
        self._show_scene(False)
        set_busy(self.send_btn, False)
        self._set_form_enabled(True)
        self.close_btn.setEnabled(True)
        self._show(message, error=True)

    def _on_sent(self, response):
        set_busy(self.send_btn, False)
        self.close_btn.setEnabled(True)
        response = response or {}
        failure = response.get("failureReason")
        if failure:
            self._show_scene(False)
            self._set_form_enabled(True)
            self._show(f"The payment didn't go through: {failure}\nNothing was recorded, so you can try again.", error=True)
            return
        if not response.get("darajaInitiated"):
            self._show_scene(False)
            self._set_form_enabled(True)
            self._show(
                "M-Pesa payouts aren't switched on for this environment, so nothing was sent. "
                "The payment was recorded and needs confirming with its receipt.",
                error=True,
            )
            return
        self.sandbox = self.sandbox or bool(response.get("sandbox"))
        self._watch(response.get("financialTransactionId"))

    # ---- watching ----------------------------------------------------

    def _watch(self, transaction_id):
        self.send_btn.setEnabled(False)
        self.close_btn.setText("Close (payment continues)")
        self._waited = 0
        self.scene.set_sending(self._fmt(self._sent_amount), self.recipient_name, stage=1, waited=0)
        self._ticker.start(1000)
        self._show("")

        def poll():
            deadline = time.monotonic() + WATCH_SECONDS
            while time.monotonic() < deadline:
                try:
                    status = self.api_client.get(f"/transactions/{transaction_id}/status")
                except Exception:
                    status = None
                if status and status.get("status") in ("COMPLETED", "FAILED"):
                    return status
                time.sleep(POLL_SECONDS)
            return {"status": "TIMED_OUT"}

        self._run(poll, self._on_result, lambda m: self._finish_error(m))

    def _tick(self):
        self._waited += 1
        self.scene.set_sending(self._fmt(self._sent_amount), self.recipient_name, stage=1, waited=self._waited)

    def _on_result(self, status):
        self._ticker.stop()
        self.bar.hide()
        self.wait_label.setText("")
        state = (status or {}).get("status")
        if state == "COMPLETED":
            self.paid = True
            receipt = status.get("referenceId")
            who = f" to {self.recipient_name}" if self.recipient_name else ""
            self.scene.set_success(self._fmt(self._sent_amount), self.recipient_name)
            text = f"Payment sent - KES {self._fmt(self._sent_amount)}{who} for {self.purpose}.\nM-Pesa receipt: {receipt}\nIt has been recorded."
            if self.sandbox:
                text += "\n(Sandbox payout - no real money moved.)"
            self._show(text, ok=True)
            self.close_btn.setText("Done")
            self.close_btn.clicked.disconnect()
            self.close_btn.clicked.connect(self.accept)
        elif state == "FAILED":
            self._finish_error(status.get("failureReason") or "M-Pesa didn't complete the payout.")
        else:
            self._finish_error(
                "M-Pesa hasn't reported back yet. If the M-Pesa callback tunnel isn't running its answer can't reach the system; "
                "check the Transactions later."
            )

    def _finish_error(self, message: str):
        self._show_scene(False)
        self._ticker.stop()
        self.bar.hide()
        self.wait_label.setText("")
        self._set_form_enabled(True)
        self.send_btn.setEnabled(True)
        self.send_btn.setText("Try again")
        self.close_btn.setText("Close")
        self._show(f"The payment didn't complete: {message}", error=True)

    def _show(self, text: str, error: bool = False, ok: bool = False):
        self.message_label.setText(text)
        self.message_label.setProperty("role", "error" if error else "")
        self.message_label.setStyleSheet("color: #4FA35C; font-weight: 600;" if ok else "")
        self.message_label.style().unpolish(self.message_label)
        self.message_label.style().polish(self.message_label)
