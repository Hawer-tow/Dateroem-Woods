"""Changing the password (23 October 2026, migration 039).

From the Profile window, or - right after signing in with a temporary password
an admin set ("is it proper to allow the superadmin to see actual passwords" -
no one can; it is reset instead) - before anything else: then the window can't
be closed, only signed out of. The computer keeps a new sign-in; every other
sign-in of the account ends.
"""
import re

from PySide6.QtWidgets import QDialog, QFrame, QHBoxLayout, QLabel, QLineEdit, QPushButton, QVBoxLayout

from app.core.api_client import ApiError, readable


def password_problem(password: str, user_name: str | None) -> str | None:
    """The same rule as the server: 8 or more characters, letters and numbers."""
    if len(password) < 8 or not re.search(r"[A-Za-z]", password) or not re.search(r"\d", password):
        return "Use at least 8 characters, with letters and numbers."
    if user_name and len(user_name) >= 3 and user_name.lower() in password.lower():
        return "Don't use your username in your password."
    return None


class PasswordChangeDialog(QDialog):
    def __init__(self, api_client, session, forced: bool = False, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.session = session
        self.forced = forced
        self.setWindowTitle("Choose a new password" if forced else "Change password")
        self.setMinimumWidth(460)

        card = QFrame()
        card.setProperty("role", "card")
        box = QVBoxLayout(card)
        box.setContentsMargins(22, 18, 22, 20)
        box.setSpacing(8)
        title = QLabel("Choose a new password" if forced else "Change password")
        title.setProperty("role", "title")
        box.addWidget(title)
        intro = QLabel(
            "Your password was reset by an admin. Enter the temporary password you were given, then a new one of your own."
            if forced else
            "Other computers and phones signed in to this account will be signed out."
        )
        intro.setProperty("role", "muted")
        intro.setWordWrap(True)
        box.addWidget(intro)
        box.addSpacing(6)

        self.current = self._field(box, "Temporary password" if forced else "Current password")
        self.new = self._field(box, "New password")
        self.again = self._field(box, "New password again")
        hint = QLabel("At least 8 characters, with letters and numbers.")
        hint.setProperty("role", "muted")
        box.addWidget(hint)
        self.error = QLabel("")
        self.error.setProperty("role", "error")
        self.error.setWordWrap(True)
        box.addWidget(self.error)

        buttons = QHBoxLayout()
        buttons.addStretch(1)
        leave = QPushButton("Sign out" if forced else "Cancel")
        leave.clicked.connect(self.reject)
        buttons.addWidget(leave)
        self.save_btn = QPushButton("Save my new password" if forced else "Change password")
        self.save_btn.setProperty("role", "primary")
        self.save_btn.setDefault(True)
        self.save_btn.clicked.connect(self._save)
        buttons.addWidget(self.save_btn)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(18, 18, 18, 16)
        layout.addWidget(card)
        layout.addLayout(buttons)
        self.current.setFocus()

    @staticmethod
    def _field(box, label: str) -> QLineEdit:
        caption = QLabel(label)
        box.addWidget(caption)
        field = QLineEdit()
        field.setEchoMode(QLineEdit.EchoMode.Password)
        box.addWidget(field)
        return field

    def _save(self):
        new = self.new.text()
        problem = password_problem(new, self.session.user_name)
        if not self.current.text():
            problem = "Enter the temporary password." if self.forced else "Enter your current password."
        elif not problem and new != self.again.text():
            problem = "The two new passwords don't match."
        if problem:
            self.error.setText(problem)
            return
        self.error.setText("")
        self.save_btn.setEnabled(False)
        try:
            result = self.api_client.post("/auth/me/password", json={"currentPassword": self.current.text(), "newPassword": new})
        except ApiError as exc:
            self.error.setText(readable(str(exc.detail)))
            self.save_btn.setEnabled(True)
            return
        self.session.token = result["token"]
        self.session.must_change_password = False
        self.accept()
