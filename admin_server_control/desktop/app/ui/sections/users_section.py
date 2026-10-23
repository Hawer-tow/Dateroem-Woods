import platform

from PySide6.QtCore import Qt, QThreadPool
from PySide6.QtGui import QColor, QGuiApplication
from PySide6.QtWidgets import (
    QCheckBox,
    QComboBox,
    QDialog,
    QFrame,
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
from app.ui.widgets.data_table import DataTable
from app.ui.widgets.record_form_dialog import FieldSpec, RecordFormDialog
from app.ui.sections.worker_details import WorkerDetailsPage

SUPER_ADMIN = "SuperAdmin"

FARMER_FIELDS = [
    FieldSpec("name", "Name", required=True),
    FieldSpec("phoneNo", "Phone"),
    FieldSpec("bankDetailsMarked", "Bank details"),
    FieldSpec("balance", "Balance (KES)", type="float", maximum=100_000_000),
]


class UsersSection(QWidget):
    """"one can even disable a certain phone user" plus "add accounts" -
    create staff accounts with any role (marketer, products_staff, worker,
    manager, admin, superadmin - unlike public signup, which always
    creates a Customer), edit their details/role, and enable/disable them.
    "place farmer under users so when i click users i can toggle from
    normal users and farmers" - Farmers is a second toggled view here
    rather than its own sidebar entry, since a farmer is also fundamentally
    "a person the system tracks", not a separate kind of thing to admins.
    The backend now actually enforces Status at login (previously present
    in the schema but never checked), so disable has a real effect."""

    def __init__(self, api_client, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        # "there can only be one super admin ... the one who permits phones
        # desktops is the superadmin" - the backend enforces it; this only
        # keeps buttons that would be refused from being offered.
        self.is_super_admin = api_client.session.role_name == SUPER_ADMIN
        self.selected_user: dict | None = None
        self.selected_farmer: dict | None = None
        self.roles: list[dict] = []
        # Every account, loaded whole so the search and role filter below
        # cover everyone, not only the newest 50 on show.
        self.all_users: list[dict] = []
        # See GenericListSection - keeps in-flight Workers from being
        # garbage-collected before their signals fire.
        self._active_workers = []

        layout = QVBoxLayout(self)

        toggle_row = QHBoxLayout()
        self.users_toggle_btn = QPushButton("Normal Users")
        self.users_toggle_btn.setProperty("role", "primary")
        self.users_toggle_btn.clicked.connect(lambda: self._switch_view(0))
        toggle_row.addWidget(self.users_toggle_btn)
        self.farmers_toggle_btn = QPushButton("Farmers")
        self.farmers_toggle_btn.clicked.connect(lambda: self._switch_view(1))
        toggle_row.addWidget(self.farmers_toggle_btn)
        self.phones_toggle_btn = QPushButton("Phones")
        self.phones_toggle_btn.clicked.connect(lambda: self._switch_view(2))
        toggle_row.addWidget(self.phones_toggle_btn)
        self.computers_toggle_btn = QPushButton("Computers")
        self.computers_toggle_btn.clicked.connect(lambda: self._switch_view(3))
        toggle_row.addWidget(self.computers_toggle_btn)
        # "worker ID number and documents" (migration 040).
        self.workers_toggle_btn = QPushButton("Worker Details")
        self.workers_toggle_btn.clicked.connect(lambda: self._switch_view(4))
        toggle_row.addWidget(self.workers_toggle_btn)
        toggle_row.addStretch()
        layout.addLayout(toggle_row)

        self.stack = QStackedWidget()
        layout.addWidget(self.stack)

        self.stack.addWidget(self._build_users_page())
        self.stack.addWidget(self._build_farmers_page())
        self.stack.addWidget(self._build_phones_page())
        self.stack.addWidget(self._build_computers_page())
        self.worker_details = WorkerDetailsPage(api_client, self.is_super_admin)
        self.stack.addWidget(self.worker_details)

        self._load_roles()
        self._load_users(self.users_table.current_limit())
        self._load_farmers(None)
        self._load_phones(None)
        self._load_computers(None)

    def _switch_view(self, index: int):
        self.stack.setCurrentIndex(index)
        toggles = (self.users_toggle_btn, self.farmers_toggle_btn, self.phones_toggle_btn, self.computers_toggle_btn, self.workers_toggle_btn)
        for position, btn in enumerate(toggles):
            btn.setProperty("role", "primary" if index == position else "")
        if index == 2:
            self._load_phones(None)
        if index == 3:
            self._load_computers(None)
        if index == 4:
            self.worker_details.load()
        for btn in toggles:
            btn.style().unpolish(btn)
            btn.style().polish(btn)

    def _run(self, fn, on_done, on_error):
        worker = Worker(fn)
        worker.signals.finished.connect(on_done)
        worker.signals.error.connect(on_error)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)

    # --- Normal Users ------------------------------------------------------

    def _build_users_page(self) -> QWidget:
        page = QWidget()
        page_layout = QVBoxLayout(page)
        page_layout.setContentsMargins(0, 0, 0, 0)

        # "in list of 50 or 100 it becomes tough to scroll through to find
        # them" - narrow the list by role, and by typing part of a name,
        # username, phone number or email; it filters as you type.
        filter_row = QHBoxLayout()
        self.user_search = QLineEdit()
        self.user_search.setPlaceholderText("Search by name, username, phone or email")
        self.user_search.setClearButtonEnabled(True)
        self.user_search.textChanged.connect(lambda _text: self._apply_user_filter())
        filter_row.addWidget(self.user_search, 1)
        filter_row.addWidget(QLabel("Role:"))
        self.role_filter = QComboBox()
        self.role_filter.setMinimumWidth(190)
        self.role_filter.addItem("All roles", None)
        self.role_filter.currentIndexChanged.connect(lambda _index: self._apply_user_filter())
        filter_row.addWidget(self.role_filter)
        # "when an account is disabled dont show it but provide a section
        # similar to show deleted but now show disabled so one can see them
        # separately and activate them again"
        filter_row.addSpacing(12)
        self.disabled_toggle = QCheckBox("Show disabled")
        self.disabled_toggle.toggled.connect(self._on_toggle_disabled)
        filter_row.addWidget(self.disabled_toggle)
        page_layout.addLayout(filter_row)

        self.users_table = DataTable("Users")
        self.users_table.refresh_requested.connect(self._load_users)
        self.users_table.row_selected.connect(self._on_user_selected)
        page_layout.addWidget(self.users_table)

        action_row = QHBoxLayout()
        self.user_selection_label = QLabel("Double-click a user to select them.")
        self.user_selection_label.setProperty("role", "muted")
        action_row.addWidget(self.user_selection_label)
        action_row.addStretch()

        self.new_user_btn = QPushButton("New Account")
        self.new_user_btn.setProperty("role", "primary")
        self.new_user_btn.clicked.connect(self._open_create_user)
        action_row.addWidget(self.new_user_btn)

        self.edit_user_btn = QPushButton("Edit")
        self.edit_user_btn.setEnabled(False)
        self.edit_user_btn.clicked.connect(self._open_edit_user)
        action_row.addWidget(self.edit_user_btn)

        # "is it proper to allow the superadmin to see actual passwords" - no
        # one can (they are stored one-way); a reset gives a temporary one,
        # shown once, which the person replaces at their next sign-in.
        self.reset_password_btn = QPushButton("Reset Password")
        self.reset_password_btn.setEnabled(False)
        self.reset_password_btn.clicked.connect(self._reset_password)
        action_row.addWidget(self.reset_password_btn)

        self.disable_btn = QPushButton("Disable Account")
        self.disable_btn.setProperty("role", "danger")
        self.disable_btn.setEnabled(False)
        self.disable_btn.clicked.connect(lambda: self._set_status(False))
        action_row.addWidget(self.disable_btn)

        self.enable_btn = QPushButton("Activate Account")
        self.enable_btn.setProperty("role", "primary")
        self.enable_btn.setEnabled(False)
        self.enable_btn.setVisible(False)  # only in the disabled list
        self.enable_btn.clicked.connect(lambda: self._set_status(True))
        action_row.addWidget(self.enable_btn)

        # "there can only be one super admin" - the role moves only by the
        # SuperAdmin handing it over, and they carry on as an Admin.
        self.hand_over_btn = QPushButton("Make SuperAdmin")
        self.hand_over_btn.setEnabled(False)
        self.hand_over_btn.setVisible(self.is_super_admin)
        self.hand_over_btn.clicked.connect(self._hand_over_super_admin)
        action_row.addWidget(self.hand_over_btn)

        page_layout.addLayout(action_row)
        return page

    def _load_roles(self):
        self._run(lambda: self.api_client.get("/roles"), self._on_roles_loaded, lambda _msg: None)

    def _on_roles_loaded(self, roles):
        self.roles = roles or []
        self._fill_role_filter()

    def _role_options(self, include_super_admin: bool = False):
        # SuperAdmin is never handed out from a form - only by "Make
        # SuperAdmin" - but it stays in the list when editing the SuperAdmin
        # themself, so their own role shows as it is.
        return [
            (role.get("name"), role.get("roleId"))
            for role in self.roles
            if include_super_admin or role.get("name") != SUPER_ADMIN
        ]

    # "i tried creating a new user the role is not working icant even see
    # the drop down" - _load_roles() above fires once, in the background,
    # at __init__; clicking New Account/Edit before that round-trip lands
    # (very plausible right after opening this tab) built the Role combo
    # from whatever self.roles was at that instant - empty - and its
    # error path swallowed a failed fetch silently, so it could stay
    # empty forever. This re-checks (and, if still empty, re-fetches and
    # waits for) self.roles immediately before either dialog opens, so
    # the combo is never built from a snapshot that isn't there yet.
    def _with_roles(self, on_ready):
        if self.roles:
            on_ready()
            return
        self.new_user_btn.setEnabled(False)
        self.edit_user_btn.setEnabled(False)

        def done(roles):
            self.roles = roles or []
            self._restore_role_buttons()
            if not self.roles:
                QMessageBox.warning(self, "No roles available", "Couldn't load account roles - try again.")
                return
            on_ready()

        def error(message):
            self._restore_role_buttons()
            QMessageBox.critical(self, "Couldn't load roles", message)

        self._run(lambda: self.api_client.get("/roles"), done, error)

    def _restore_role_buttons(self):
        self.new_user_btn.setEnabled(True)
        self.edit_user_btn.setEnabled(
            bool(self.selected_user) and (self.is_super_admin or self.selected_user.get("roleName") != SUPER_ADMIN)
        )

    def _open_create_user(self):
        self._with_roles(self._open_create_user_dialog)

    def _open_create_user_dialog(self):
        fields = [
            FieldSpec("userName", "Username", required=True),
            FieldSpec("name", "Full name", required=True),
            FieldSpec("password", "Password", required=True),
            FieldSpec("email", "Email"),
            FieldSpec("phoneNo", "Phone"),
            FieldSpec("roleId", "Role", type="combo", required=True, options=self._role_options()),
        ]
        dialog = RecordFormDialog(
            "New Account",
            fields,
            save_fn=lambda values: self.api_client.post("/users", json=values),
            parent=self,
        )
        if dialog.exec():
            self._load_users(self.users_table.current_limit())

    def _open_edit_user(self):
        if not self.selected_user:
            return
        self._with_roles(self._open_edit_user_dialog)

    def _open_edit_user_dialog(self):
        user_id = self.selected_user.get("userId")
        # "for super admin allow them to edit a username for a user, since
        # one might have used a wrong username when creating" - only the
        # SuperAdmin sees the field (the backend refuses anyone else).
        username_field = [FieldSpec("userName", "Username", required=True)] if self.is_super_admin else []
        fields = username_field + [
            FieldSpec("name", "Full name", required=True),
            FieldSpec("email", "Email"),
            FieldSpec("phoneNo", "Phone"),
            FieldSpec(
                "roleId", "Role", type="combo", required=True,
                options=self._role_options(include_super_admin=self.selected_user.get("roleName") == SUPER_ADMIN),
            ),
            # "only one phone the company phone will have the ability to
            # read the offline SMS messages" - checking this for one
            # account automatically unchecks it for whoever had it before
            # (UserService.update enforces "only ever one"), so this is
            # also where you'd see who currently holds it.
            FieldSpec("canSyncSms", "Company phone (can sync M-Pesa SMS)", type="checkbox"),
        ]
        dialog = RecordFormDialog(
            f"Edit Account #{user_id}",
            fields,
            save_fn=lambda values: self.api_client.patch(f"/users/{user_id}", json=values),
            initial_values=self.selected_user,
            parent=self,
        )
        if dialog.exec():
            self._load_users(self.users_table.current_limit())

    def _load_users(self, _limit=None):
        # Always every account (a few hundred at most): the "Show" limit is
        # applied after filtering, in _apply_user_filter.
        self.users_table.set_loading()
        self._run(lambda: self.api_client.get("/users"), self._on_users_loaded, self.users_table.set_error)

    def _on_users_loaded(self, rows):
        self.all_users = rows or []
        disabled = sum(1 for user in self.all_users if not user.get("status"))
        self.disabled_toggle.setText(f"Show disabled ({disabled})")
        self._fill_role_filter()
        self._apply_user_filter()

    def _listed_users(self):
        """Active accounts normally; only the disabled ones while "Show
        disabled" is ticked."""
        showing_disabled = self.disabled_toggle.isChecked()
        return [user for user in self.all_users if bool(user.get("status")) != showing_disabled]

    def _on_toggle_disabled(self, checked: bool):
        self.selected_user = None
        self.user_selection_label.setText(
            "Disabled accounts - double-click one to select it, then Activate Account."
            if checked else "Double-click a user to select them."
        )
        self.disable_btn.setVisible(not checked)
        self.enable_btn.setVisible(checked)
        self.new_user_btn.setVisible(not checked)
        self.hand_over_btn.setVisible(self.is_super_admin and not checked)
        for button in (self.disable_btn, self.enable_btn, self.edit_user_btn, self.hand_over_btn):
            button.setEnabled(False)
        self._fill_role_filter()
        self._apply_user_filter()

    # The role list with how many accounts each has, e.g. "Customer (5)";
    # keeps whichever role was picked across reloads.
    def _fill_role_filter(self):
        picked = self.role_filter.currentData()
        listed = self._listed_users()
        counts: dict = {}
        for user in listed:
            counts[user.get("roleId")] = counts.get(user.get("roleId"), 0) + 1
        roles = [(role.get("roleId"), role.get("name")) for role in self.roles]
        known = {role_id for role_id, _ in roles}
        # A role the roles list didn't include still gets an entry.
        for user in self.all_users:
            if user.get("roleId") not in known:
                roles.append((user.get("roleId"), user.get("roleName")))
                known.add(user.get("roleId"))
        self.role_filter.blockSignals(True)
        self.role_filter.clear()
        self.role_filter.addItem(f"All roles ({len(listed)})", None)
        for role_id, name in sorted(roles, key=lambda item: str(item[1] or "")):
            self.role_filter.addItem(f"{name} ({counts.get(role_id, 0)})", role_id)
        index = self.role_filter.findData(picked)
        self.role_filter.setCurrentIndex(index if index >= 0 else 0)
        self.role_filter.blockSignals(False)

    def _apply_user_filter(self):
        query = self.user_search.text().strip().lower()
        digits = "".join(ch for ch in query if ch.isdigit())
        role_id = self.role_filter.currentData()

        def matches(user):
            if role_id is not None and user.get("roleId") != role_id:
                return False
            if not query:
                return True
            words = " ".join(str(user.get(key) or "") for key in ("name", "userName", "email", "phoneNo")).lower()
            if query in words:
                return True
            # Phone numbers match however they're typed: 07..., 7..., 254...
            if len(digits) >= 3:
                phone = "".join(ch for ch in str(user.get("phoneNo") or "") if ch.isdigit())
                core = digits[1:] if digits.startswith("0") else digits[3:] if digits.startswith("254") else digits
                return bool(core) and core in phone
            return False

        listed = self._listed_users()
        found = [user for user in listed if matches(user)]
        limit = self.users_table.current_limit()
        shown = found[:limit] if limit else found
        filtering = bool(query) or role_id is not None
        if filtering:
            total = len(listed)
        else:
            total = len(found) if len(shown) < len(found) else None
        self.users_table.set_rows(shown, total=total)
        if filtering and not found:
            self.users_table.count_label.setText("No account matches")
        elif not listed and self.disabled_toggle.isChecked():
            self.users_table.count_label.setText("No disabled accounts")

    def _on_user_selected(self, user: dict):
        self.selected_user = user
        active = bool(user.get("status"))
        target_is_super = user.get("roleName") == SUPER_ADMIN
        # Only the SuperAdmin may change the SuperAdmin's account, and it
        # can't be disabled at all (approvals would stop) - hand it over first.
        editable = self.is_super_admin or not target_is_super
        note = "" if editable else " - only the SuperAdmin can change this account"
        self.user_selection_label.setText(
            f"Selected: {user.get('userName')} ({user.get('roleName')}) - currently {'active' if active else 'disabled'}{note}"
        )
        self.disable_btn.setEnabled(active and not target_is_super)
        self.enable_btn.setEnabled(not active and editable)
        self.edit_user_btn.setEnabled(editable)
        # Not your own (Profile does that), never the SuperAdmin's, and an
        # Admin's only by the SuperAdmin - the backend checks the same.
        is_self = user.get("userId") == self.api_client.session.user_id
        target_is_admin = user.get("roleName") == "Admin"
        self.reset_password_btn.setEnabled(
            not is_self and not target_is_super and (self.is_super_admin or not target_is_admin)
        )
        self.hand_over_btn.setEnabled(
            self.is_super_admin and active and not target_is_super and user.get("roleName") != "Customer"
        )

    def _hand_over_super_admin(self):
        user = self.selected_user
        if not user:
            return
        super_role = next((role.get("roleId") for role in self.roles if role.get("name") == SUPER_ADMIN), None)
        if super_role is None:
            QMessageBox.warning(self, "Roles not loaded", "Couldn't load account roles - try again.")
            return
        question = (
            f"Make {user.get('name') or user.get('userName')} ({user.get('userName')}) the SuperAdmin?\n\n"
            "There is only one SuperAdmin: from now on they approve and block phones and computers, "
            "and you become an Admin. You will be asked to sign in again."
        )
        if QMessageBox.question(self, "Hand over SuperAdmin?", question) != QMessageBox.StandardButton.Yes:
            return
        user_id = user.get("userId")

        def done(_result):
            # This sign-in still says SuperAdmin - start a fresh one.
            self.api_client.signals.session_ended.emit(
                f"You handed the SuperAdmin role to {user.get('userName')}. Sign in again as an Admin."
            )

        self._run(
            lambda: self.api_client.patch(f"/users/{user_id}", json={"roleId": super_role}),
            done,
            lambda message: QMessageBox.critical(self, "Couldn't hand over", message),
        )

    def _reset_password(self):
        user = self.selected_user
        if not user:
            return
        who = f"{user.get('name') or user.get('userName')} ({user.get('userName')})"
        question = (
            f"Reset the password for {who}?\n\n"
            "They are signed out everywhere at once, and must choose a new password the next time they sign in. "
            "You will see a temporary password once - give it to them in person or by phone, never in a group chat."
        )
        if QMessageBox.question(self, "Reset password?", question) != QMessageBox.StandardButton.Yes:
            return
        user_id = user.get("userId")
        self.reset_password_btn.setEnabled(False)

        def done(result):
            self.reset_password_btn.setEnabled(True)
            TemporaryPasswordDialog(who, (result or {}).get("temporaryPassword") or "", parent=self).exec()
            self._load_users(self.users_table.current_limit())

        def failed(message: str):
            self.reset_password_btn.setEnabled(True)
            QMessageBox.critical(self, "Couldn't reset the password", message)

        self._run(lambda: self.api_client.post(f"/users/{user_id}/reset-password"), done, failed)

    def _set_status(self, active: bool):
        if not self.selected_user:
            return
        user_id = self.selected_user.get("userId")
        user_name = self.selected_user.get("userName")

        verb = "enable" if active else "disable"
        confirm = QMessageBox.question(
            self,
            f"{verb.capitalize()} account?",
            f"{verb.capitalize()} the account for '{user_name}'?"
            + ("" if active else "\nThey will no longer be able to sign in."),
        )
        if confirm != QMessageBox.StandardButton.Yes:
            return

        self._run(
            lambda: self.api_client.patch(f"/users/{user_id}/status", json={"active": active}),
            lambda _result: self._load_users(self.users_table.current_limit()),
            lambda message: QMessageBox.critical(self, "Couldn't update account", message),
        )

    # --- Farmers -------------------------------------------------------

    def _build_farmers_page(self) -> QWidget:
        page = QWidget()
        page_layout = QVBoxLayout(page)
        page_layout.setContentsMargins(0, 0, 0, 0)

        self.farmers_table = DataTable("Farmers", show_limit_selector=False, searchable=True, search_placeholder="Search farmers by name or phone")
        self.farmers_table.refresh_requested.connect(self._load_farmers)
        self.farmers_table.row_selected.connect(self._on_farmer_selected)
        page_layout.addWidget(self.farmers_table)

        action_row = QHBoxLayout()
        self.farmer_selection_label = QLabel("Double-click a farmer to select them.")
        self.farmer_selection_label.setProperty("role", "muted")
        action_row.addWidget(self.farmer_selection_label)
        action_row.addStretch()

        self.new_farmer_btn = QPushButton("New Farmer")
        self.new_farmer_btn.setProperty("role", "primary")
        self.new_farmer_btn.clicked.connect(self._open_create_farmer)
        action_row.addWidget(self.new_farmer_btn)

        self.edit_farmer_btn = QPushButton("Edit")
        self.edit_farmer_btn.setEnabled(False)
        self.edit_farmer_btn.clicked.connect(self._open_edit_farmer)
        action_row.addWidget(self.edit_farmer_btn)

        page_layout.addLayout(action_row)
        return page

    def _load_farmers(self, _limit):
        self.farmers_table.set_loading()
        self._run(
            lambda: self.api_client.get("/farmers"),
            lambda rows: self.farmers_table.set_rows(rows or []),
            self.farmers_table.set_error,
        )

    def _on_farmer_selected(self, row: dict):
        self.selected_farmer = row
        self.farmer_selection_label.setText(f"Selected: {row.get('name')} (#{row.get('farmerId')})")
        self.edit_farmer_btn.setEnabled(True)

    def _open_create_farmer(self):
        dialog = RecordFormDialog(
            "New Farmer",
            FARMER_FIELDS,
            save_fn=lambda values: self.api_client.post("/farmers", json=values),
            parent=self,
        )
        if dialog.exec():
            self._load_farmers(None)

    def _open_edit_farmer(self):
        if not self.selected_farmer:
            return
        farmer_id = self.selected_farmer.get("farmerId")
        dialog = RecordFormDialog(
            f"Edit Farmer #{farmer_id}",
            FARMER_FIELDS,
            save_fn=lambda values: self.api_client.patch(f"/farmers/{farmer_id}", json=values),
            initial_values=self.selected_farmer,
            parent=self,
        )
        if dialog.exec():
            self._load_farmers(None)

    # --- Phones ----------------------------------------------------------
    # "allows only one user per account per mobile phone" - every phone that
    # has signed in to the mobile app. An account's first phone is approved
    # automatically; any other phone waits here until an admin approves it
    # (only one approved phone per account), and revoking a phone signs it
    # out at once.

    PHONE_STATUS_TEXT = {"PENDING": "Waiting for approval", "ACTIVE": "Approved", "REVOKED": "Blocked"}

    def _build_phones_page(self) -> QWidget:
        page = QWidget()
        page_layout = QVBoxLayout(page)
        page_layout.setContentsMargins(0, 0, 0, 0)

        intro = QLabel(
            "Each account may use the mobile app on one approved phone. New phones wait here for approval; "
            "approving one for an account that already has a phone replaces the old phone."
            + ("" if self.is_super_admin else " Only the SuperAdmin can approve or block phones.")
        )
        intro.setWordWrap(True)
        intro.setProperty("role", "muted")
        page_layout.addWidget(intro)

        self.phones_table = DataTable("Phones", show_limit_selector=False, searchable=True, search_placeholder="Search by account, name or phone model")
        self.phones_table.refresh_requested.connect(self._load_phones)
        self.phones_table.row_selected.connect(self._on_phone_selected)
        page_layout.addWidget(self.phones_table)

        action_row = QHBoxLayout()
        self.phone_selection_label = QLabel("Double-click a phone to select it.")
        self.phone_selection_label.setProperty("role", "muted")
        action_row.addWidget(self.phone_selection_label)
        action_row.addStretch()

        self.approve_phone_btn = QPushButton("Approve")
        self.approve_phone_btn.setProperty("role", "primary")
        self.approve_phone_btn.setEnabled(False)
        self.approve_phone_btn.clicked.connect(self._approve_phone)
        action_row.addWidget(self.approve_phone_btn)

        self.revoke_phone_btn = QPushButton("Block")
        self.revoke_phone_btn.setProperty("role", "danger")
        self.revoke_phone_btn.setEnabled(False)
        self.revoke_phone_btn.clicked.connect(self._revoke_phone)
        action_row.addWidget(self.revoke_phone_btn)

        page_layout.addLayout(action_row)
        self.phones: dict[int, dict] = {}
        self.selected_phone: dict | None = None
        return page

    def _load_phones(self, _limit):
        self.phones_table.set_loading()
        self._run(lambda: self.api_client.get("/devices"), self._on_phones_loaded, self.phones_table.set_error)

    def _on_phones_loaded(self, devices):
        devices = devices or []
        self.phones = {d.get("deviceId"): d for d in devices}
        rows = [
            {
                "Phone #": d.get("deviceId"),
                "Account": d.get("userName"),
                "Name": d.get("name"),
                "Role": d.get("roleName"),
                "Phone": d.get("deviceModel"),
                "Android": d.get("osVersion"),
                "Status": self.PHONE_STATUS_TEXT.get(d.get("status"), d.get("status")),
                "First seen": _short_time(d.get("createdAt")),
                "Last used": _short_time(d.get("lastSeenAt")),
                # An account's first phone is approved by itself (Approved_By = its own user).
                "Approved by": "Automatic (first phone)" if d.get("approvedBy") and d.get("approvedBy") == d.get("userId") else (d.get("approvedByName") or ""),
            }
            for d in devices
        ]
        waiting = sum(1 for d in devices if d.get("status") == "PENDING")
        self.phones_toggle_btn.setText(f"Phones ({waiting} waiting)" if waiting else "Phones")
        self.phones_table.set_rows(rows, row_color=_phone_row_color)
        self.selected_phone = None
        self.approve_phone_btn.setEnabled(False)
        self.revoke_phone_btn.setEnabled(False)
        self.phone_selection_label.setText("Double-click a phone to select it.")

    def _on_phone_selected(self, row: dict):
        device = self.phones.get(row.get("Phone #"))
        if not device:
            return
        self.selected_phone = device
        status = device.get("status")
        self.phone_selection_label.setText(
            f"Selected: {device.get('deviceModel') or 'phone'} for {device.get('userName')} - "
            f"{self.PHONE_STATUS_TEXT.get(status, status).lower()}"
        )
        self.approve_phone_btn.setEnabled(self.is_super_admin and status != "ACTIVE")
        self.revoke_phone_btn.setEnabled(self.is_super_admin and status != "REVOKED")
        self.revoke_phone_btn.setText("Reject" if status == "PENDING" else "Block")

    def _approve_phone(self):
        device = self.selected_phone
        if not device:
            return
        current = next(
            (d for d in self.phones.values() if d.get("userId") == device.get("userId") and d.get("status") == "ACTIVE"),
            None,
        )
        phone = device.get("deviceModel") or "this phone"
        if current:
            question = (
                f"{device.get('userName')} already uses {current.get('deviceModel') or 'another phone'}.\n\n"
                f"Approve {phone} and block the old phone? The old phone is signed out at once."
            )
        else:
            question = f"Approve {phone} for {device.get('userName')}?"
        if QMessageBox.question(self, "Approve phone?", question) != QMessageBox.StandardButton.Yes:
            return
        device_id = device.get("deviceId")
        replace = "true" if current else "false"
        self._run(
            lambda: self.api_client.post(f"/devices/{device_id}/approve?replace={replace}"),
            lambda _result: self._load_phones(None),
            lambda message: QMessageBox.critical(self, "Couldn't approve phone", message),
        )

    def _revoke_phone(self):
        device = self.selected_phone
        if not device:
            return
        pending = device.get("status") == "PENDING"
        phone = device.get("deviceModel") or "this phone"
        question = (
            f"Reject {phone} for {device.get('userName')}? It will not be able to sign in."
            if pending
            else f"Block {phone} for {device.get('userName')}? It is signed out at once and cannot sign in again."
        )
        if QMessageBox.question(self, "Reject phone?" if pending else "Block phone?", question) != QMessageBox.StandardButton.Yes:
            return
        device_id = device.get("deviceId")
        self._run(
            lambda: self.api_client.post(f"/devices/{device_id}/revoke"),
            lambda _result: self._load_phones(None),
            lambda message: QMessageBox.critical(self, "Couldn't block phone", message),
        )

    # --- Computers -------------------------------------------------------
    # "a standalone application that has higher privilege ... share it as an
    # application to other admins" - every computer the desktop app has been
    # signed in on. One approved computer per account; only the SuperAdmin
    # approves or blocks them (their own first computer was approved by
    # itself), and blocking one signs it out at once.

    def _build_computers_page(self) -> QWidget:
        page = QWidget()
        page_layout = QVBoxLayout(page)
        page_layout.setContentsMargins(0, 0, 0, 0)

        intro = QLabel(
            "Each Admin or SuperAdmin account may use this app on one approved computer. New computers wait here "
            "for approval; approving one for an account that already has a computer replaces the old one."
            + ("" if self.is_super_admin else " Only the SuperAdmin can approve or block computers.")
        )
        intro.setWordWrap(True)
        intro.setProperty("role", "muted")
        page_layout.addWidget(intro)

        self.computers_table = DataTable(
            "Computers", show_limit_selector=False, searchable=True, search_placeholder="Search by account, name or computer"
        )
        self.computers_table.refresh_requested.connect(self._load_computers)
        self.computers_table.row_selected.connect(self._on_computer_selected)
        page_layout.addWidget(self.computers_table)

        action_row = QHBoxLayout()
        self.computer_selection_label = QLabel("Double-click a computer to select it.")
        self.computer_selection_label.setProperty("role", "muted")
        action_row.addWidget(self.computer_selection_label)
        action_row.addStretch()

        self.approve_computer_btn = QPushButton("Approve")
        self.approve_computer_btn.setProperty("role", "primary")
        self.approve_computer_btn.setEnabled(False)
        self.approve_computer_btn.clicked.connect(self._approve_computer)
        action_row.addWidget(self.approve_computer_btn)

        self.revoke_computer_btn = QPushButton("Block")
        self.revoke_computer_btn.setProperty("role", "danger")
        self.revoke_computer_btn.setEnabled(False)
        self.revoke_computer_btn.clicked.connect(self._revoke_computer)
        action_row.addWidget(self.revoke_computer_btn)

        page_layout.addLayout(action_row)
        self.computers: dict[int, dict] = {}
        self.selected_computer: dict | None = None
        return page

    def _load_computers(self, _limit):
        self.computers_table.set_loading()
        self._run(lambda: self.api_client.get("/computers"), self._on_computers_loaded, self.computers_table.set_error)

    def _on_computers_loaded(self, computers):
        computers = computers or []
        self.computers = {c.get("computerId"): c for c in computers}
        rows = [
            {
                "Computer #": c.get("computerId"),
                "Account": c.get("userName"),
                "Name": c.get("name"),
                "Role": c.get("roleName"),
                "Computer": c.get("computerName"),
                "Windows": c.get("osVersion"),
                "App": c.get("appVersion"),
                "Status": self.PHONE_STATUS_TEXT.get(c.get("status"), c.get("status")),
                "First seen": _short_time(c.get("createdAt")),
                "Last used": _short_time(c.get("lastSeenAt")),
                # The SuperAdmin's first computer is approved by itself (Approved_By = its own user).
                "Approved by": "Automatic (SuperAdmin's first)" if c.get("approvedBy") and c.get("approvedBy") == c.get("userId") else (c.get("approvedByName") or ""),
            }
            for c in computers
        ]
        waiting = sum(1 for c in computers if c.get("status") == "PENDING")
        self.computers_toggle_btn.setText(f"Computers ({waiting} waiting)" if waiting else "Computers")
        self.computers_table.set_rows(rows, row_color=_phone_row_color)
        self.selected_computer = None
        self.approve_computer_btn.setEnabled(False)
        self.revoke_computer_btn.setEnabled(False)
        self.computer_selection_label.setText("Double-click a computer to select it.")

    def _on_computer_selected(self, row: dict):
        computer = self.computers.get(row.get("Computer #"))
        if not computer:
            return
        self.selected_computer = computer
        status = computer.get("status")
        self.computer_selection_label.setText(
            f"Selected: {computer.get('computerName') or 'computer'} for {computer.get('userName')} - "
            f"{self.PHONE_STATUS_TEXT.get(status, status).lower()}"
        )
        self.approve_computer_btn.setEnabled(self.is_super_admin and status != "ACTIVE")
        self.revoke_computer_btn.setEnabled(self.is_super_admin and status != "REVOKED")
        self.revoke_computer_btn.setText("Reject" if status == "PENDING" else "Block")

    def _approve_computer(self):
        computer = self.selected_computer
        if not computer:
            return
        current = next(
            (c for c in self.computers.values() if c.get("userId") == computer.get("userId") and c.get("status") == "ACTIVE"),
            None,
        )
        name = computer.get("computerName") or "this computer"
        if current:
            question = (
                f"{computer.get('userName')} already uses {current.get('computerName') or 'another computer'}.\n\n"
                f"Approve {name} and block the old computer? The old computer is signed out at once."
            )
        else:
            question = f"Approve {name} for {computer.get('userName')}?"
        if QMessageBox.question(self, "Approve computer?", question) != QMessageBox.StandardButton.Yes:
            return
        computer_id = computer.get("computerId")
        replace = "true" if current else "false"
        self._run(
            lambda: self.api_client.post(f"/computers/{computer_id}/approve?replace={replace}"),
            lambda _result: self._load_computers(None),
            lambda message: QMessageBox.critical(self, "Couldn't approve computer", message),
        )

    def _revoke_computer(self):
        computer = self.selected_computer
        if not computer:
            return
        pending = computer.get("status") == "PENDING"
        name = computer.get("computerName") or "this computer"
        question = (
            f"Reject {name} for {computer.get('userName')}? It will not be able to sign in."
            if pending
            else f"Block {name} for {computer.get('userName')}? It is signed out at once and cannot sign in again."
        )
        if (not pending and computer.get("userId") == self.api_client.session.user_id
                and computer.get("computerName") == platform.node()):
            question += "\n\nThis looks like the computer you are using now - you would be signed out here too."
        if QMessageBox.question(self, "Reject computer?" if pending else "Block computer?", question) != QMessageBox.StandardButton.Yes:
            return
        computer_id = computer.get("computerId")
        self._run(
            lambda: self.api_client.post(f"/computers/{computer_id}/revoke"),
            lambda _result: self._load_computers(None),
            lambda message: QMessageBox.critical(self, "Couldn't block computer", message),
        )


def _short_time(value) -> str:
    # "2026-10-03T14:05:22" -> "2026-10-03 14:05"
    text = str(value or "")
    return text.replace("T", " ")[:16]


def _phone_row_color(row: dict):
    # Waiting phones stand out so they are dealt with first.
    return QColor(235, 154, 75, 60) if row.get("Status") == "Waiting for approval" else None


class TemporaryPasswordDialog(QDialog):
    """The temporary password, shown this once (it is never stored readable)."""

    def __init__(self, who: str, password: str, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Temporary password")
        self.setMinimumWidth(460)
        card = QFrame()
        card.setProperty("role", "card")
        box = QVBoxLayout(card)
        box.setContentsMargins(22, 18, 22, 20)
        box.setSpacing(10)
        title = QLabel("Temporary password")
        title.setProperty("role", "title")
        box.addWidget(title)
        intro = QLabel(f"For {who}. It is shown only now. They sign in with it and then choose their own password.")
        intro.setProperty("role", "muted")
        intro.setWordWrap(True)
        box.addWidget(intro)
        value = QLineEdit(password)
        value.setReadOnly(True)
        font = value.font()
        font.setFamily("Consolas")
        font.setPointSizeF(font.pointSizeF() * 1.5)
        font.setBold(True)
        value.setFont(font)
        value.setAlignment(Qt.AlignmentFlag.AlignCenter)
        box.addWidget(value)
        self.copied = QLabel("")
        self.copied.setProperty("role", "muted")
        box.addWidget(self.copied)
        row = QHBoxLayout()
        row.addStretch(1)
        copy = QPushButton("Copy")
        copy.clicked.connect(lambda: (QGuiApplication.clipboard().setText(password), self.copied.setText("Copied.")))
        row.addWidget(copy)
        done = QPushButton("Done")
        done.setProperty("role", "primary")
        done.clicked.connect(self.accept)
        row.addWidget(done)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(18, 18, 18, 16)
        layout.addWidget(card)
        layout.addLayout(row)
