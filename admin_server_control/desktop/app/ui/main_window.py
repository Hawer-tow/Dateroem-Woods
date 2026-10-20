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
from PySide6.QtCore import QSize
from app.core import server_address
from app.ui import theme
from app.ui.icons import icon

# Screens that keep what they show until asked (no reload on coming back).
NO_AUTO_RELOAD = {"Database Browser", "Servers"}


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

        central = QWidget()
        self.setCentralWidget(central)
        root_layout = QHBoxLayout(central)

        sidebar_container = QVBoxLayout()
        self.sidebar = QListWidget()
        self.sidebar.setIconSize(QSize(18, 18))
        sidebar_container.addWidget(self.sidebar, 1)

        # Settings sits with Sign Out at the foot of the sidebar - always in
        # view, below however many sections there are (20 October 2026).
        self.settings_btn = QPushButton("Settings")
        self.settings_btn.setIcon(icon("settings"))
        self.settings_btn.clicked.connect(self._open_settings)
        sidebar_container.addWidget(self.settings_btn)

        self.logout_btn = QPushButton("Sign Out")
        self.logout_btn.clicked.connect(self._sign_out)
        sidebar_container.addWidget(self.logout_btn)

        sidebar_widget = QWidget()
        sidebar_widget.setFixedWidth(210)
        sidebar_widget.setLayout(sidebar_container)
        root_layout.addWidget(sidebar_widget)

        # "placing distance from the left side so it doesn't feel like it's
        # connected to it" - breathing room between the menu and whatever
        # section is open, and around its edges.
        root_layout.addSpacing(18)
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
            self.sidebar.addItem(QListWidgetItem(icon(icon_name, theme.WOOD, selected=theme.WOOD_ON), label))
            self.stack.addWidget(QWidget())  # placeholder, replaced on first visit

        self.sidebar.currentRowChanged.connect(self._show_section)
        self.sidebar.setCurrentRow(start_section if 0 <= start_section < self.sidebar.count() else 0)

        status = QStatusBar()
        status.showMessage(f"Signed in as {session.name} ({session.role_name})")
        self.setStatusBar(status)

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
