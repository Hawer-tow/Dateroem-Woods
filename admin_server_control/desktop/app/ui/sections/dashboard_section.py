from datetime import date

from PySide6.QtCore import QThreadPool, Qt
from PySide6.QtWidgets import QFrame, QGridLayout, QHBoxLayout, QLabel, QVBoxLayout, QWidget

from app.core.worker import Worker
from app.ui import theme
from app.ui.icons import pixmap

SUPER_ADMIN = "SuperAdmin"
# Staff roles in the order they are listed under Users.
STAFF_ROLES = (("SuperAdmin", "SuperAdmin", "SuperAdmin"), ("Admin", "admin", "admins"),
               ("Marketer", "marketer", "marketers"), ("Employee", "employee", "employees"))


def _plural(n: int, one: str, many: str) -> str:
    return f"{n} {one if n == 1 else many}"


class _StatCard(QFrame):
    """One figure on the dashboard, with a short breakdown beneath it. In the
    modern look (theme.MODERN) a tile on a see-through card: the section's
    icon in a soft wood circle, the figure large, its name and the
    breakdown quiet beneath."""

    def __init__(self, title: str, icon_name: str = "", parent=None):
        super().__init__(parent)
        self.setObjectName("statCard")
        self.details_label = QLabel("")
        self.details_label.setProperty("role", "muted")
        self.details_label.setWordWrap(True)
        self.details_label.hide()
        if not theme.MODERN:
            self.setStyleSheet(theme.card_style())
            layout = QVBoxLayout(self)
            self.title_label = QLabel(title)
            self.title_label.setProperty("role", "muted")
            self.value_label = QLabel("...")
            self.value_label.setProperty("role", "title")
            layout.addWidget(self.title_label)
            layout.addWidget(self.value_label)
            layout.addWidget(self.details_label)
            return

        # Only the card itself - its labels are QFrames too and must stay plain.
        self.setStyleSheet(theme.card_style("QFrame#statCard"))
        self.setMinimumHeight(128)
        layout = QHBoxLayout(self)
        layout.setContentsMargins(18, 16, 18, 16)
        layout.setSpacing(16)
        badge = QLabel()
        badge.setFixedSize(46, 46)
        badge.setAlignment(Qt.AlignmentFlag.AlignCenter)
        badge.setPixmap(pixmap(icon_name, 22, theme.ACCENT if theme.IS_LIGHT else theme.WOOD))
        badge.setStyleSheet("background-color: rgba(235, 154, 75, 0.14); border-radius: 23px;")
        layout.addWidget(badge, 0, Qt.AlignmentFlag.AlignTop)
        text = QVBoxLayout()
        text.setSpacing(0)
        self.value_label = QLabel("…")
        self.value_label.setStyleSheet('font-family: "Segoe UI Variable Display", "Segoe UI"; font-size: 30px; font-weight: 600;')
        self.title_label = QLabel(title)
        self.title_label.setStyleSheet("font-weight: 600;")
        self.details_label.setStyleSheet("font-size: 12px;")
        text.addWidget(self.value_label)
        text.addWidget(self.title_label)
        text.addSpacing(6)
        text.addWidget(self.details_label)
        text.addStretch()
        layout.addLayout(text, 1)

    def set_value(self, value):
        self.value_label.setText(str(value))

    def set_details(self, lines):
        """A short breakdown under the figure, one line per item of `lines`."""
        text = "\n".join(line for line in lines if line)
        self.details_label.setText(text)
        self.details_label.setVisible(bool(text))


class DashboardSection(QWidget):
    """Landing screen - a quick read on the whole system before drilling
    into any one section, same spirit as the mobile app's own dashboard.

    Each figure carries a short breakdown (21 October 2026: "add information
    e.g customers ... staff ... products retired deleted active campaign
    photos videos ... trip"): users by customers and staff roles, products in
    the shop, retired, deleted and out of stock, campaigns running and their
    photos and videos, trips in progress and finished. The database figure
    is for the SuperAdmin only."""

    def __init__(self, session, api_client, parent=None):
        super().__init__(parent)
        self.session = session
        self.api_client = api_client
        # See GenericListSection - keeps in-flight Workers from being
        # garbage-collected before their signals fire.
        self._active_workers = []
        self._products = None
        self._deleted_products = None
        self._campaigns = None
        self._deleted_campaigns = None

        layout = QVBoxLayout(self)
        self.heading = QLabel(f"Welcome, {session.name} ({session.role_name})")
        self.heading.setProperty("role", "title")
        layout.addWidget(self.heading)
        if theme.MODERN:
            subtitle = QLabel("The whole system at a glance.")
            subtitle.setProperty("role", "muted")
            layout.addWidget(subtitle)
            layout.addSpacing(12)

        self.is_super_admin = session.role_name == SUPER_ADMIN
        self.users_card = _StatCard("Users", "users")
        self.products_card = _StatCard("Products", "treeRings")
        self.campaigns_card = _StatCard("Campaigns", "megaphone")
        self.trips_card = _StatCard("Trips", "map")
        self.tables_card = _StatCard("Database tables", "database") if self.is_super_admin else None
        cards = [self.users_card, self.products_card, self.campaigns_card, self.trips_card]
        if self.tables_card is not None:
            cards.append(self.tables_card)
        columns = 3 if len(cards) > 4 else 2
        grid = QGridLayout()
        grid.setSpacing(14 if theme.MODERN else 6)
        for i, card in enumerate(cards):
            grid.addWidget(card, i // columns, i % columns)
        layout.addLayout(grid)
        layout.addStretch()

        self.refresh()

    def refresh(self):
        if self.tables_card is not None:
            self._load("/admin/tables", self._show_tables)
        self._load("/users", self._show_users)
        self._load("/products", lambda d: self._set_products(d, None))
        self._load("/products/deleted", lambda d: self._set_products(None, d))
        self._load("/marketing/campaigns?includeRetired=true", lambda d: self._set_campaigns(d, None))
        self._load("/marketing/campaigns/deleted", lambda d: self._set_campaigns(None, d))
        self._load("/trips", self._show_trips)

    # --- figures --------------------------------------------------------

    def _show_tables(self, rows):
        self.tables_card.set_value(len(rows or []))
        self.tables_card.set_details(["Only the SuperAdmin sees this"])

    def _show_users(self, rows):
        rows = rows or []
        self.users_card.set_value(len(rows))
        customers = sum(1 for u in rows if u.get("roleName") == "Customer")
        staff = len(rows) - customers
        by_role = []
        for role, one, many in STAFF_ROLES:
            n = sum(1 for u in rows if u.get("roleName") == role)
            if n:
                by_role.append(_plural(n, one, many) if role != SUPER_ADMIN else role)
        disabled = sum(1 for u in rows if not u.get("status"))
        self.users_card.set_details([
            f"{_plural(customers, 'customer', 'customers')} · {staff} staff",
            " · ".join(by_role),
            f"{disabled} disabled" if disabled else "",
        ])

    def _set_products(self, kept, deleted):
        if kept is not None:
            self._products = kept
        if deleted is not None:
            self._deleted_products = deleted
        if self._products is None:
            return
        rows = self._products
        retired = sum(1 for p in rows if p.get("isRetired"))
        in_shop = [p for p in rows if not p.get("isRetired")]
        out_of_stock = sum(1 for p in in_shop if float(p.get("stock") or 0) <= 0)
        self.products_card.set_value(len(in_shop))
        lines = [f"in the shop · {retired} retired"
                 + (f" · {len(self._deleted_products)} deleted" if self._deleted_products is not None else "")]
        if out_of_stock:
            lines.append(f"{out_of_stock} out of stock")
        self.products_card.set_details(lines)

    def _set_campaigns(self, kept, deleted):
        if kept is not None:
            self._campaigns = kept
        if deleted is not None:
            self._deleted_campaigns = deleted
        if self._campaigns is None:
            return
        retired = sum(1 for c in self._campaigns if c.get("isRetired"))
        rows = [c for c in self._campaigns if not c.get("isRetired")]
        today = date.today().isoformat()
        running = sum(1 for c in rows if (c.get("startAt") or "") <= today <= (c.get("endAt") or "9999-12-31"))
        upcoming = sum(1 for c in rows if (c.get("startAt") or "") > today)
        videos = sum(1 for c in rows if (c.get("mediaType") or "").upper() == "VIDEO")
        photos = sum(1 for c in rows if (c.get("mediaType") or "").upper() == "IMAGE")
        self.campaigns_card.set_value(len(rows))
        first = f"{running} running now"
        if upcoming:
            first += f" · {upcoming} upcoming"
        first += f" · {len(rows) - running - upcoming} ended"
        if retired:
            first += f" · {retired} retired"
        media = f"{_plural(photos, 'photo', 'photos')} · {_plural(videos, 'video', 'videos')}"
        if self._deleted_campaigns:
            media += f" · {len(self._deleted_campaigns)} deleted"
        self.campaigns_card.set_details([first, media])

    def _show_trips(self, rows):
        rows = rows or []
        in_progress = sum(1 for t in rows if not t.get("endDate"))
        deliveries = sum(int(t.get("noOfDeliveries") or 0) for t in rows)
        self.trips_card.set_value(len(rows))
        if not rows:
            self.trips_card.set_details(["None recorded yet - start one from the phone app"])
            return
        self.trips_card.set_details([
            f"{in_progress} in progress · {len(rows) - in_progress} finished",
            _plural(deliveries, "delivery made", "deliveries made"),
        ])

    def _load(self, path, on_done):
        worker = Worker(lambda: self.api_client.get(path))
        worker.signals.finished.connect(on_done)
        worker.signals.error.connect(lambda _msg: None)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)
