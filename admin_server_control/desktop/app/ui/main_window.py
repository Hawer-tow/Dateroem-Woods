import time

from PySide6.QtWidgets import (
    QHBoxLayout,
    QListWidget,
    QListWidgetItem,
    QMainWindow,
    QPushButton,
    QStackedWidget,
    QStatusBar,
    QVBoxLayout,
    QWidget,
)

from app.ui.widgets.data_table import DataTable
from app.ui.sections.dashboard_section import DashboardSection
from app.ui.sections.database_browser_section import DatabaseBrowserSection
from app.ui.sections.delivery_areas_section import DeliveryAreasSection
from app.ui.sections.faqs_section import FaqsSection
from app.ui.sections.insights_section import InsightsSection
from app.ui.sections.marketing_section import MarketingSection
from app.ui.sections.products_section import ProductsSection
from app.ui.sections.reviews_section import ReviewsSection
from app.ui.sections.trips_section import TripsSection
from app.ui.sections.users_section import UsersSection
from PySide6.QtCore import QSettings, QSize, Qt
from app.core import server_address
from app.ui import theme
from app.ui.backdrop import Backdrop, NavDelegate, NavPane
from app.ui.icons import icon

# Screens that keep what they show until asked (no reload on coming back).
NO_AUTO_RELOAD = {"Database Browser", "Servers"}

# The menu's width in the modern look: open (icon and name) and folded (icons).
MENU_WIDTH = 188
MENU_WIDTH_FOLDED = 62


def _server_panel(server_manager, session):
    from app.ui.widgets.server_control_panel import ServerControlPanel  # only in the owner's copy

    # Everyone can start servers; only the SuperAdmin can stop them.
    return ServerControlPanel(server_manager, can_stop=lambda: session.role_name == "SuperAdmin")


class MainWindow(QMainWindow):
    """The main shell - a sidebar of sections and a stacked content area,
    covering "all the sections products, marketing, transactions etc"
    plus the two admin-only additions this app exists for: Users (with
    the real enable/disable) and the raw Database Browser."""

    def __init__(self, session, api_client, server_manager, on_logout, on_theme_changed=None, start_section=0, parent=None):
        super().__init__(parent)
        self.session = session
        self.api_client = api_client
        self.server_manager = server_manager
        self.on_logout = on_logout
        # main.py's restyle_main_window: rebuilds this window in a new colour
        # theme, on the section given, without signing in again.
        self.on_theme_changed = on_theme_changed

        # Left blank on purpose: Qt auto-appends " - <applicationDisplayName>"
        # to every window title once that's set (see main.py), so an
        # explicit "Dateroem Admin Control" here would double up.
        self.resize(1150, 740)

        # The modern look (theme.MODERN, ui/backdrop.py): the window stands on
        # a painted backdrop and the menu floats on it - narrower, without a
        # panel, and able to fold down to its icons.
        central = Backdrop() if theme.MODERN else QWidget()
        self.setCentralWidget(central)
        root_layout = QHBoxLayout(central)
        if theme.MODERN:
            root_layout.setContentsMargins(6, 6, 10, 4)

        sidebar_container = QVBoxLayout()
        if theme.MODERN:
            sidebar_container.setContentsMargins(10, 10, 8, 10)
            sidebar_container.setSpacing(2)
            self.nav_toggle = QPushButton()
            self.nav_toggle.setObjectName("navToggle")
            self.nav_toggle.setIcon(icon("menu"))
            self.nav_toggle.setIconSize(QSize(18, 18))
            self.nav_toggle.setFixedSize(40, 36)
            self.nav_toggle.clicked.connect(lambda: self._fold_menu(not self._menu_folded))
            sidebar_container.addWidget(self.nav_toggle)
            sidebar_container.addSpacing(6)
        self.sidebar = QListWidget()
        self.sidebar.setIconSize(QSize(18, 18))
        if theme.MODERN:
            self.sidebar.setObjectName("navList")
            self._nav_delegate = NavDelegate(self.sidebar)
            self.sidebar.setItemDelegate(self._nav_delegate)
            self.sidebar.setMouseTracking(True)
            self.sidebar.setFrameShape(QListWidget.Shape.NoFrame)
            self.sidebar.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        sidebar_container.addWidget(self.sidebar, 1)

        # "desktop profile above Settings" (23 October 2026): the signed-in
        # admin's photo, details and password; the button shows their photo.
        self.profile_btn = QPushButton("Profile")
        self.profile_btn.setIcon(icon("user", theme.TEXT_MUTED) if theme.MODERN else icon("user"))
        self.profile_btn.clicked.connect(self._open_profile)
        sidebar_container.addWidget(self.profile_btn)

        # Settings sits with Sign Out at the foot of the sidebar - always in
        # view, below however many sections there are (20 October 2026).
        self.settings_btn = QPushButton("Settings")
        self.settings_btn.setIcon(icon("settings", theme.TEXT_MUTED) if theme.MODERN else icon("settings"))
        self.settings_btn.clicked.connect(self._open_settings)
        sidebar_container.addWidget(self.settings_btn)

        self.logout_btn = QPushButton("Sign Out")
        self.logout_btn.clicked.connect(self._sign_out)
        sidebar_container.addWidget(self.logout_btn)
        if theme.MODERN:
            for button in (self.profile_btn, self.settings_btn, self.logout_btn):
                button.setObjectName("navAction")
                button.setIconSize(QSize(18, 18))
            self.logout_btn.setText("Sign out")
            self.logout_btn.setIcon(icon("logout", theme.TEXT_MUTED))

        self.sidebar_widget = NavPane() if theme.MODERN else QWidget()
        self.sidebar_widget.setFixedWidth(MENU_WIDTH if theme.MODERN else 210)
        self.sidebar_widget.setLayout(sidebar_container)
        root_layout.addWidget(self.sidebar_widget)

        # "placing distance from the left side so it doesn't feel like it's
        # connected to it" - breathing room between the menu and whatever
        # section is open, and around its edges.
        root_layout.addSpacing(10 if theme.MODERN else 18)
        self.stack = QStackedWidget()
        self.stack.setContentsMargins(4, 10, 12, 8)
        root_layout.addWidget(self.stack, 1)

        # Only the section actually being looked at gets built - building
        # all seven up front (each with its own DataTable(s) and its own
        # network calls firing immediately) was most of what made the
        # sign-in -> main window switch feel slow, which is what made the
        # login-window-disappears-before-main-window-appears gap (fixed in
        # main.py's show_main_window) so noticeable in the first place.
        # Each row's widget is built the first time it's actually selected
        # and kept after that, not rebuilt on every click back to it.
        self._section_factories = [
            ("home", "Dashboard", lambda: DashboardSection(session, api_client)),
            ("lightbulb", "Insights", lambda: InsightsSection(api_client)),
            ("treeRings", "Products", lambda: ProductsSection(api_client)),
            ("megaphone", "Marketing", lambda: MarketingSection(api_client)),
            ("star", "Ratings", lambda: ReviewsSection(api_client)),
            ("message", "FAQs", lambda: FaqsSection(api_client)),
            ("map", "Trips & Finance", lambda: TripsSection(api_client)),
            ("pin", "Delivery Areas", lambda: DeliveryAreasSection(api_client)),
            ("users", "Users", lambda: UsersSection(api_client)),
            ("database", "Database Browser", lambda: DatabaseBrowserSection(api_client)),
        ]
        # Starting and stopping servers only works where they run - not for
        # an admin signing in from another computer, nor once the servers
        # are with a hosting provider.
        if server_manager is not None and server_address.is_this_computer():
            self._section_factories.append(("server", "Servers", lambda: _server_panel(server_manager, session)))
        self._section_widgets: dict[int, QWidget] = {}
        self._last_loaded: dict[int, float] = {}
        for icon_name, label, _factory in self._section_factories:
            if theme.MODERN:
                # Quiet icons in the text colour; the chosen one turns wood.
                entry_icon = icon(icon_name, theme.TEXT_MUTED, selected=theme.ACCENT if theme.IS_LIGHT else theme.WOOD)
            else:
                entry_icon = icon(icon_name, theme.WOOD, selected=theme.WOOD_ON)
            item = QListWidgetItem(entry_icon, label)
            item.setToolTip(label)
            self.sidebar.addItem(item)
            self.stack.addWidget(QWidget())  # placeholder, replaced on first visit

        self.sidebar.currentRowChanged.connect(self._show_section)
        self.sidebar.setCurrentRow(start_section if 0 <= start_section < self.sidebar.count() else 0)

        self._menu_folded = False
        if theme.MODERN:
            self._fold_menu(QSettings("Dateroem", "AdminControl").value("menu/folded", False, type=bool), save=False)

        status = QStatusBar()
        status.showMessage(f"Signed in as {session.name} ({session.role_name})")
        self.setStatusBar(status)

        # "a timer like about 25 minutes of inactivity" - locks this window
        # after the SuperAdmin's minutes untouched (ui/idle_lock.py).
        from app.ui.idle_lock import IdleLock

        self.idle_lock = IdleLock(self, api_client, session, on_sign_out=lambda reason: self.on_logout(reason))
        self._load_profile_photo()

    def closeEvent(self, event):
        self.idle_lock.stop()
        super().closeEvent(event)

    def _load_profile_photo(self):
        """The Profile button shows the admin's own photo once it has loaded."""
        from PySide6.QtCore import QThreadPool
        from PySide6.QtGui import QIcon, QPixmap

        from app.core import http
        from app.core.worker import Worker
        from app.ui.profile_dialog import round_photo

        def work():
            me = self.api_client.get("/auth/me")
            url = (me or {}).get("photoUrl")
            if not url:
                return None
            response = http.session().get(server_address.absolute(url), timeout=15)
            response.raise_for_status()
            return response.content

        def done(content):
            pixmap = QPixmap()
            if content and pixmap.loadFromData(content):
                self.profile_btn.setIcon(QIcon(round_photo(pixmap, 36)))
            else:
                self.profile_btn.setIcon(icon("user", theme.TEXT_MUTED) if theme.MODERN else icon("user"))

        self._photo_worker = Worker(work)
        self._photo_worker.signals.finished.connect(done)
        self._photo_worker.signals.error.connect(lambda _m: None)
        QThreadPool.globalInstance().start(self._photo_worker)

    def _open_profile(self):
        from app.ui.profile_dialog import ProfileDialog

        dialog = ProfileDialog(self.api_client, self.session, self)
        dialog.exec()
        if dialog.photo_changed:
            self._load_profile_photo()
        self.statusBar().showMessage(f"Signed in as {self.session.name} ({self.session.role_name})")

    def _show_section(self, index: int):
        if index not in self._section_widgets:
            _icon, _label, factory = self._section_factories[index]
            widget = factory()
            self._section_widgets[index] = widget
            placeholder = self.stack.widget(index)
            self.stack.removeWidget(placeholder)
            self.stack.insertWidget(index, widget)
            placeholder.deleteLater()
            self._last_loaded[index] = time.monotonic()
        else:
            self._reload_section(index)
        self.stack.setCurrentIndex(index)

    def _reload_section(self, index: int):
        """Sections are built once and kept, so without this a section
        showed whatever it loaded the first time you opened it - forever.
        Coming back to one now reloads its data (at most every 10s, so
        flicking between tabs doesn't hammer the server)."""
        if time.monotonic() - self._last_loaded.get(index, 0) < 10:
            return
        widget = self._section_widgets.get(index)
        if widget is None or self._section_factories[index][1] in NO_AUTO_RELOAD:
            return
        self._last_loaded[index] = time.monotonic()
        refresh = getattr(widget, "refresh", None)
        if callable(refresh):
            refresh()
            return
        for table in widget.findChildren(DataTable):
            table.refresh_requested.emit(table.current_limit())

    def _fold_menu(self, folded: bool, save: bool = True):
        """Folds the menu down to its icons (names on hover) or opens it
        again; remembered on this computer."""
        self._menu_folded = folded
        self._nav_delegate.folded = folded
        self.sidebar_widget.setFixedWidth(MENU_WIDTH_FOLDED if folded else MENU_WIDTH)
        self.nav_toggle.setToolTip("Open the menu" if folded else "Fold the menu to icons")
        for button, label in ((self.profile_btn, "Profile"), (self.settings_btn, "Settings"), (self.logout_btn, "Sign out")):
            button.setText("" if folded else label)
            button.setToolTip(label if folded else "")
        self.sidebar.viewport().update()
        if save:
            QSettings("Dateroem", "AdminControl").setValue("menu/folded", folded)

    def _open_settings(self):
        from app.ui.settings_dialog import SettingsDialog

        dialog = SettingsDialog(self.api_client, self.session, self)
        dialog.exec()
        if dialog.theme_changed and self.on_theme_changed is not None:
            self.on_theme_changed(self.sidebar.currentRow())

    def _sign_out(self):
        # on_logout (main.py's show_login_window) shows the login window
        # and then closes this one - not the other way around, so there's
        # always something on screen during the switch. See show_main_window's
        # own comment in main.py for the same reasoning in reverse.
        self.on_logout()
