from pathlib import Path

# Same palettes as the website and mobile app (DateroemBackground/Surface/
# Accent/Text/TextMuted), so this reads as the same product rather than a
# bolted-on third UI.
#
# "is it possible to add more themes like the dark and light theme and what
# we have will be default ... the theme is still the one they choose last"
# (20 October 2026): Forest - the board's Living Forest greens - is the
# default; Dark and Light are chosen in Settings (ui/settings_dialog.py) and
# saved with the account, so the website and the phone app open in them too.
# set_theme() swaps the values below and rebuilds STYLESHEET; the sign-in
# screen always shows Forest, like its forest photograph.
PALETTES = {
    "forest": {
        "BACKGROUND": "#0f241b", "SURFACE": "#1d4232", "SURFACE_ALT": "#173628", "TABLE_ALT": "#295a45",
        "TEXT": "#f5f5f1", "TEXT_MUTED": "#b3c2b4", "DANGER": "#e5484d",
        "ACCENT": "#eb9a4b", "ACCENT_HOVER": "#f4b566", "INK": "255, 255, 255",
        "RETIRED_TINT": "#3c2e1c", "IS_LIGHT": False,
    },
    # Charcoal greys with the same wood, for anyone who finds the green tiring.
    "dark": {
        "BACKGROUND": "#121417", "SURFACE": "#1e2124", "SURFACE_ALT": "#181b1e", "TABLE_ALT": "#2a2e33",
        "TEXT": "#f2f2ef", "TEXT_MUTED": "#a9b1b6", "DANGER": "#e5484d",
        "ACCENT": "#eb9a4b", "ACCENT_HOVER": "#f4b566", "INK": "255, 255, 255",
        "RETIRED_TINT": "#3a2f22", "IS_LIGHT": False,
    },
    # Warm paper with dark text; lines and text in a darker, barkier wood so
    # they stay readable on white (the painted planks are the same).
    "light": {
        "BACKGROUND": "#f4f1ea", "SURFACE": "#ffffff", "SURFACE_ALT": "#ece6da", "TABLE_ALT": "#f6f2ea",
        "TEXT": "#1c2520", "TEXT_MUTED": "#57635b", "DANGER": "#c5292e",
        "ACCENT": "#a4561a", "ACCENT_HOVER": "#8a4512", "INK": "0, 0, 0",
        "RETIRED_TINT": "#efe2cf", "IS_LIGHT": True,
    },
}
THEME_LABELS = {"forest": "Forest", "dark": "Dark", "light": "Light"}
DEFAULT_THEME = "forest"
CURRENT = DEFAULT_THEME

BACKGROUND = "#0f241b"
SURFACE = "#1d4232"
SURFACE_ALT = "#173628"
# "since this wood for areas previously having the orange color looks good
# you can extend it to the mobile and desktop" - the flat amber accent is now
# the website's wood: one sapwood tone wherever a single colour is needed
# (focus borders, selection tints), the painted plank for primary buttons
# and the selected sidebar entry, and the website's honey-to-orange paint as
# a sweep along accent strips. The images in assets/wood are rendered from
# the website's own painter (frontend/website: `npm run wood:export`).
WOOD = "#eb9a4b"
WOOD_LIGHT = "#f4b566"
WOOD_ON = "#2a1508"  # dark bark brown - text on painted wood
# ACCENT: the wood as a line or text colour (focus borders, links) - the
# same as WOOD except on Light, where it is darker to stay readable.
ACCENT = WOOD
ACCENT_HOVER = WOOD_LIGHT
WOOD_SWEEP = "qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 #fcdca6, stop:0.3 #f4b566, stop:0.6 #e8892f, stop:1 #f4b566)"
_WOOD_DIR = (Path(__file__).resolve().parent / "assets" / "wood").as_posix()
WOOD_PLANK = f"{_WOOD_DIR}/wood_plank.png"
WOOD_PLANK_HOVER = f"{_WOOD_DIR}/wood_plank_hover.png"
WORDMARK = f"{_WOOD_DIR}/dateroem_wordmark@2x.png"
# The company emblem ("the new one will take over" from the drawn pine, 18
# October 2026), written by the website's `npm run brand:export`: the full
# drawing for large places (the intro), the bold one for small ones (the
# sign-in card). The window icon is app/assets/icon.ico, from the same export.
_BRAND_DIR = (Path(__file__).resolve().parent / "assets" / "brand").as_posix()
EMBLEM = f"{_BRAND_DIR}/emblem.png"
EMBLEM_BOLD = f"{_BRAND_DIR}/emblem_bold.png"
# The entrance pictures, the same as the phone app's (19 October 2026), made by
# brand/entrance photos/prepare_entrance.py --desktop.
_ENTRANCE_DIR = (Path(__file__).resolve().parent / "assets" / "entrance").as_posix()
ENTRANCE_FOREST = f"{_ENTRANCE_DIR}/entrance_forest.jpg"
ENTRANCE_FOG = f"{_ENTRANCE_DIR}/entrance_fog.png"


def wordmark_pixmap(width: int, device_pixel_ratio: float = 1.0):
    """The tree-ring Dateroem wordmark, `width` logical pixels wide and sharp
    on high-DPI screens (scaled down from the 2x master)."""
    from PySide6.QtCore import Qt
    from PySide6.QtGui import QPixmap

    pixmap = QPixmap(WORDMARK).scaledToWidth(
        round(width * device_pixel_ratio), Qt.TransformationMode.SmoothTransformation
    )
    pixmap.setDevicePixelRatio(device_pixel_ratio)
    return pixmap


def emblem_pixmap(size: int, device_pixel_ratio: float = 1.0, bold: bool = False):
    """The company emblem, `size` logical pixels square, sharp on high-DPI screens."""
    from PySide6.QtCore import Qt
    from PySide6.QtGui import QPixmap

    pixmap = QPixmap(EMBLEM_BOLD if bold else EMBLEM).scaledToWidth(
        round(size * device_pixel_ratio), Qt.TransformationMode.SmoothTransformation
    )
    pixmap.setDevicePixelRatio(device_pixel_ratio)
    return pixmap


TEXT = "#f5f5f1"
TEXT_MUTED = "#b3c2b4"
DANGER = "#e5484d"
# Hairlines and faint tints: white on the dark themes, black on Light.
INK = "255, 255, 255"
IS_LIGHT = False
RETIRED_TINT = "#3c2e1c"
BORDER = f"rgba({INK}, 0.12)"
# Tables: every other row a shade lighter, and a faint line between rows.
# "after each row the background color is different ... deepen the color so
# that it's more visible" - a clearly lighter stripe on every other row.
TABLE_ALT = "#295a45"
ROW_LINE = f"rgba({INK}, 0.06)"
# Status colours for table cells (green good, amber waiting, red stopped,
# blue for neutral labels such as roles) - see widgets/data_table.py.
STATUS_GOOD = "#4fa35c"
STATUS_WAIT = "#e0a03a"
STATUS_BAD = "#e5484d"
STATUS_INFO = "#5b9bd5"

# The modern look (21 October 2026): "the green doesn't feel like one color
# ... make it look modern" - the main window stands on a painted backdrop with
# depth (ui/backdrop.py: a glow fading into the theme's darkest shade, with a
# fine grain; rain on glass behind the Dark theme's menu), the menu floats on
# it without a panel and can fold down to icons, and content sits on slightly
# see-through cards. Approved by the owner on 21 October 2026 ("they look
# good proceed"); DATEROEM_MODERN_UI=0 brings back the earlier flat look.
import os as _os

MODERN = _os.environ.get("DATEROEM_MODERN_UI", "1") == "1"
# DIALOG_BACKDROP: pop-up windows get the main window's depth too ("so that
# they don't feel like they are not part of the new structure", 21 October
# 2026) - the same glow from the top-left corner fading into the darkest
# shade, as one radial gradient (ui/backdrop.py paints the main window's).
_MODERN_LAYERS = {
    "forest": {"LAYER": "rgba(29, 66, 50, 0.55)", "LAYER_BORDER": "rgba(255, 255, 255, 0.08)", "CONSOLE_BG": "rgba(4, 12, 8, 0.45)",
               "DIALOG_BACKDROP": "qradialgradient(cx:0.1, cy:-0.15, radius:1.3, fx:0.1, fy:-0.15, stop:0 #2b6448, stop:0.5 #11291e, stop:1 #0a1610)"},
    "dark": {"LAYER": "rgba(44, 48, 54, 0.55)", "LAYER_BORDER": "rgba(255, 255, 255, 0.07)", "CONSOLE_BG": "rgba(0, 0, 0, 0.35)",
             "DIALOG_BACKDROP": "qradialgradient(cx:0.1, cy:-0.15, radius:1.3, fx:0.1, fy:-0.15, stop:0 #30353c, stop:0.5 #17191c, stop:1 #0d0e10)"},
    "light": {"LAYER": "rgba(255, 255, 255, 0.80)", "LAYER_BORDER": "rgba(0, 0, 0, 0.07)", "CONSOLE_BG": "rgba(255, 255, 255, 0.70)",
              "DIALOG_BACKDROP": "qradialgradient(cx:0.1, cy:-0.15, radius:1.3, fx:0.1, fy:-0.15, stop:0 #f4f6ee, stop:0.5 #f6f2e9, stop:1 #ebe5d8)"},
}
for _name, _values in _MODERN_LAYERS.items():
    PALETTES[_name].update(_values)
LAYER = _MODERN_LAYERS["forest"]["LAYER"]
LAYER_BORDER = _MODERN_LAYERS["forest"]["LAYER_BORDER"]
CONSOLE_BG = _MODERN_LAYERS["forest"]["CONSOLE_BG"]
DIALOG_BACKDROP = _MODERN_LAYERS["forest"]["DIALOG_BACKDROP"]
# The tick drawn in a ticked box (the box is painted wood): dark bark.
CHECK_MARK = (Path(__file__).resolve().parent / "assets" / "ui" / "check.png").as_posix()


def card_style(selector: str = "QFrame", radius: int = 8) -> str:
    """Background for a card-like frame that sets its own style (the
    dashboard's figures, for instance): a see-through layer in the modern
    look, the solid surface otherwise."""
    if MODERN:
        return f"{selector} {{ background-color: {LAYER}; border: 1px solid {LAYER_BORDER}; border-radius: 12px; }}"
    return f"{selector} {{ background-color: {SURFACE}; border-radius: {radius}px; }}"


def build_modern_stylesheet() -> str:
    """What the modern look changes, on top of build_stylesheet(): see-through
    containers over the backdrop, layered cards, quieter controls, underlined
    tabs, larger titles in Windows 11's own font. Windows of their own
    (dialogs, menus, drop-down lists) stay solid."""
    return f"""
QWidget {{
    background-color: transparent;
    font-family: "Segoe UI Variable Text", "Segoe UI";
}}
QMainWindow {{
    background-color: {BACKGROUND};
}}
QDialog, QMessageBox {{
    background-color: {DIALOG_BACKDROP};
}}
QCheckBox::indicator, QAbstractItemView::indicator {{
    width: 16px;
    height: 16px;
    border-radius: 4px;
    border: 1px solid rgba({INK}, 0.40);
    background-color: rgba({INK}, 0.05);
}}
QCheckBox::indicator:hover, QAbstractItemView::indicator:hover {{
    border-color: {ACCENT};
}}
QCheckBox::indicator:checked, QAbstractItemView::indicator:checked {{
    background-color: {WOOD};
    border-color: {WOOD};
    image: url({CHECK_MARK});
}}
QCheckBox::indicator:disabled {{
    border-color: rgba({INK}, 0.18);
    background-color: transparent;
}}
QMenu, QToolTip {{
    background-color: {SURFACE};
    color: {TEXT};
    border: 1px solid {BORDER};
    border-radius: 6px;
}}
QComboBox QAbstractItemView {{
    background-color: {SURFACE};
    border: 1px solid {BORDER};
    selection-background-color: rgba(235, 154, 75, 0.28);
}}
QCalendarWidget QWidget#qt_calendar_navigationbar, QCalendarWidget QAbstractItemView {{
    background-color: {SURFACE};
}}
QLabel[role="title"] {{
    font-family: "Segoe UI Variable Display", "Segoe UI";
    font-size: 22px;
    font-weight: 600;
}}
QFrame[role="card"], QFrame#tableCard {{
    background-color: {LAYER};
    border: 1px solid {LAYER_BORDER};
    border-radius: 12px;
}}
QFrame[role="card"] {{
    border-top: 2px solid {WOOD_SWEEP};
}}
QTableWidget {{
    background-color: transparent;
    alternate-background-color: rgba({INK}, 0.035);
}}
QTableWidget::item:hover {{
    background-color: rgba({INK}, 0.05);
}}
QHeaderView::section, QTableCornerButton::section {{
    background-color: transparent;
}}
QPushButton {{
    background-color: rgba({INK}, 0.06);
    border: 1px solid rgba({INK}, 0.10);
    border-radius: 6px;
    padding: 6px 14px;
}}
QPushButton:hover {{
    background-color: rgba({INK}, 0.11);
    border-color: rgba({INK}, 0.18);
}}
QPushButton:pressed {{
    background-color: rgba({INK}, 0.04);
}}
QLineEdit, QComboBox, QSpinBox, QDoubleSpinBox, QDateEdit, QTextEdit {{
    background-color: rgba({INK}, 0.05);
    border: 1px solid rgba({INK}, 0.12);
    border-radius: 6px;
}}
QLineEdit:focus, QComboBox:focus, QDateEdit:focus, QTextEdit:focus {{
    border: 1px solid {ACCENT};
    background-color: rgba({INK}, 0.08);
}}
QTabWidget::pane {{
    background-color: {LAYER};
    border: 1px solid {LAYER_BORDER};
    border-radius: 10px;
    top: -1px;
}}
QTabBar::tab {{
    background-color: transparent;
    border: none;
    border-bottom: 2px solid transparent;
    padding: 8px 14px;
    margin-right: 2px;
}}
QTabBar::tab:selected {{
    background-color: transparent;
    color: {TEXT};
    border: none;
    border-bottom: 2px solid {WOOD};
}}
QTabBar::tab:hover:!selected {{
    background-color: rgba({INK}, 0.05);
    border-radius: 6px;
}}
QPlainTextEdit {{
    background-color: {CONSOLE_BG};
    border: 1px solid {LAYER_BORDER};
    border-radius: 8px;
}}
QStatusBar {{
    background-color: transparent;
}}
QScrollBar:vertical {{
    width: 8px;
}}
QScrollBar:horizontal {{
    height: 8px;
}}
QListWidget#navList {{
    background-color: transparent;
    border: none;
    padding: 0;
}}
QPushButton#navAction, QPushButton#navToggle {{
    background-color: transparent;
    border: none;
    border-radius: 8px;
    color: {TEXT_MUTED};
    text-align: left;
    padding: 9px 12px;
}}
QPushButton#navAction:hover, QPushButton#navToggle:hover {{
    background-color: rgba({INK}, 0.07);
    color: {TEXT};
}}
"""


def full_stylesheet() -> str:
    return build_stylesheet() + (build_modern_stylesheet() if MODERN else "")

def build_stylesheet() -> str:
    """The app's stylesheet in the current theme's colours."""
    return f"""
QWidget {{
    background-color: {BACKGROUND};
    color: {TEXT};
    font-size: 13px;
}}
QMainWindow, QDialog {{
    background-color: {BACKGROUND};
}}
QLabel {{
    background-color: transparent;
}}
QLabel[role="title"] {{
    font-size: 18px;
    font-weight: 700;
    color: {TEXT};
}}
QLabel[role="muted"] {{
    color: {TEXT_MUTED};
}}
QLabel[role="error"] {{
    color: {DANGER};
}}
QLabel[role="success"] {{
    color: {STATUS_GOOD};
    font-weight: 600;
}}
QFrame#profileCard {{
    background-color: rgba({INK}, 0.05);
    border: 1px solid rgba({INK}, 0.09);
    border-radius: 14px;
}}
QFrame#profileCard:hover {{
    background-color: rgba({INK}, 0.10);
    border-color: {WOOD};
}}
QLabel#profileName {{
    color: {TEXT};
    font-weight: 700;
    background: transparent;
}}
QLabel#profileRole {{
    color: {TEXT_MUTED};
    font-size: 11px;
    background: transparent;
}}
QFrame[role="card"] {{
    background-color: {SURFACE};
    border: 1px solid {BORDER};
    border-top: 3px solid {WOOD_SWEEP};
    border-radius: 14px;
}}
QLineEdit[role="login-field"] {{
    background-color: {SURFACE_ALT};
    border: 1px solid {BORDER};
    border-radius: 10px;
    padding: 13px 14px;
    font-size: 14px;
}}
QLineEdit[role="login-field"]:focus {{
    border: 1.5px solid {ACCENT};
    background-color: {SURFACE};
}}
QPushButton {{
    background-color: {SURFACE};
    color: {TEXT};
    border: 1px solid {BORDER};
    border-radius: 6px;
    padding: 6px 14px;
}}
QPushButton:hover {{
    background-color: {SURFACE_ALT};
    border-color: {ACCENT};
}}
QPushButton:disabled {{
    color: {TEXT_MUTED};
}}
QPushButton[role="primary"] {{
    background-color: {WOOD};
    background-image: url({WOOD_PLANK});
    background-position: center;
    color: {WOOD_ON};
    font-weight: 600;
    border: 1px solid rgba(74, 38, 14, 0.55);
}}
QPushButton[role="primary"]:hover {{
    background-image: url({WOOD_PLANK_HOVER});
    border-color: rgba(74, 38, 14, 0.8);
}}
QPushButton[role="primary"]:disabled {{
    color: rgba(42, 21, 8, 0.55);
}}
QTableWidget[loading="true"] {{
    color: {TEXT_MUTED};
}}
QFrame#tableCard[flash="true"] {{
    border: 2px solid {ACCENT};
}}
QDateEdit[unset="true"] {{
    color: {TEXT_MUTED};
}}
QCalendarWidget QWidget {{
    alternate-background-color: {SURFACE_ALT};
}}
QCalendarWidget QToolButton {{
    color: {TEXT};
    background-color: {SURFACE};
    font-size: 16px;
    font-weight: 600;
    padding: 6px 10px;
    border-radius: 6px;
}}
QCalendarWidget QToolButton:hover {{
    background-color: {SURFACE_ALT};
    border: 1px solid {ACCENT};
}}
QCalendarWidget QAbstractItemView {{
    font-size: 15px;
    background-color: {SURFACE};
    color: {TEXT};
    selection-background-color: {WOOD};
    selection-color: {WOOD_ON};
}}
QCalendarWidget QAbstractItemView:disabled {{
    color: {TEXT_MUTED};
}}
QPushButton[highlight="true"] {{
    background-color: {WOOD};
    background-image: url({WOOD_PLANK});
    background-position: center;
    color: {WOOD_ON};
    font-weight: 700;
    border: 2px solid {TEXT};
}}
QPushButton[role="danger"] {{
    color: {DANGER};
}}
QLineEdit, QComboBox, QSpinBox, QDoubleSpinBox, QDateEdit {{
    background-color: {SURFACE};
    color: {TEXT};
    border: 1px solid {BORDER};
    border-radius: 6px;
    padding: 6px 8px;
}}
QLineEdit:focus, QComboBox:focus, QDateEdit:focus {{
    border-color: {ACCENT};
}}
QListWidget {{
    background-color: {SURFACE};
    border: none;
    outline: none;
    padding: 6px;
}}
QListWidget::item {{
    padding: 10px 12px;
    border-radius: 6px;
    color: {TEXT_MUTED};
}}
QListWidget::item:selected {{
    background-color: {WOOD};
    background-image: url({WOOD_PLANK});
    background-position: center;
    color: {WOOD_ON};
    font-weight: 600;
}}
QListWidget::item:hover:!selected {{
    background-color: {SURFACE_ALT};
}}
/* "the display of the tables in the old rectangles feels so outdated" -
   a rounded card (QFrame#tableCard, widgets/data_table.py) holding the
   table: roomy rows, no grid lines (a faint line between rows instead), and
   a quiet header with a thin wood rule under it. A table whose columns fit
   sits in the middle of the card at its own width; a wider one fills the
   card (keeping the space from its left edge) and scrolls. */
QFrame#tableCard {{
    background-color: {SURFACE};
    border: 1px solid {BORDER};
    border-radius: 12px;
}}
QTableWidget {{
    background-color: {SURFACE};
    alternate-background-color: {TABLE_ALT};
    border: none;
    selection-background-color: transparent;
}}
QTableWidget::item {{
    padding: 4px 12px;
    border: none;
    border-bottom: 1px solid {ROW_LINE};
}}
QTableWidget::item:selected {{
    background-color: rgba(235, 154, 75, 0.28);
    color: {TEXT};
}}
QHeaderView {{
    background-color: transparent;
    border: none;
}}
QHeaderView::section {{
    background-color: {SURFACE};
    color: {TEXT_MUTED};
    padding: 9px 12px;
    border: none;
    border-bottom: 2px solid rgba(235, 154, 75, 0.45);
    font-weight: 600;
}}
QTableCornerButton::section {{
    background-color: {SURFACE};
    border: none;
}}
QStatusBar {{
    background-color: {SURFACE};
    color: {TEXT_MUTED};
}}
QTabWidget::pane {{
    border: 1px solid {BORDER};
    border-radius: 6px;
    top: -1px;
}}
QTabBar::tab {{
    background-color: {SURFACE};
    color: {TEXT_MUTED};
    padding: 8px 16px;
    border: 1px solid {BORDER};
    border-bottom: none;
    border-top-left-radius: 6px;
    border-top-right-radius: 6px;
    margin-right: 2px;
}}
QTabBar::tab:selected {{
    background-color: {SURFACE_ALT};
    color: {TEXT};
    font-weight: 600;
    border-top: 2px solid {WOOD_SWEEP};
}}
QTabBar::tab:hover:!selected {{
    color: {TEXT};
}}
QPlainTextEdit {{
    background-color: {SURFACE_ALT};
    color: {TEXT};
    border: 1px solid {BORDER};
    border-radius: 6px;
}}
/* "when scrolling towards left or right ... let the scroll pad ... feel like
   it's part of the app's theme" - slim wood-toned handles on a clear track,
   the same both ways, no arrow buttons. */
QScrollBar:vertical {{
    background: transparent;
    width: 10px;
    margin: 4px 2px 4px 0;
}}
QScrollBar:horizontal {{
    background: transparent;
    height: 10px;
    margin: 0 4px 2px 4px;
}}
QScrollBar::handle:vertical, QScrollBar::handle:horizontal {{
    background: rgba(235, 154, 75, 0.38);
    border-radius: 4px;
}}
QScrollBar::handle:vertical {{
    min-height: 32px;
}}
QScrollBar::handle:horizontal {{
    min-width: 32px;
}}
QScrollBar::handle:vertical:hover, QScrollBar::handle:horizontal:hover,
QScrollBar::handle:vertical:pressed, QScrollBar::handle:horizontal:pressed {{
    background: {WOOD};
}}
QScrollBar::add-line, QScrollBar::sub-line {{
    width: 0;
    height: 0;
    border: none;
    background: none;
}}
QScrollBar::add-page, QScrollBar::sub-page {{
    background: none;
}}
"""


STYLESHEET = full_stylesheet()


def set_theme(name: str | None) -> str:
    """Switches every colour above to `name`'s ('forest', 'dark' or 'light';
    anything else is Forest), rebuilds STYLESHEET and applies it to the app.
    Widgets read theme.X when they are built or painted, so a window built
    afterwards - or repainted - shows the new colours. Returns the name used."""
    global CURRENT, BORDER, ROW_LINE, STYLESHEET
    name = name if name in PALETTES else DEFAULT_THEME
    CURRENT = name
    globals().update(PALETTES[name])
    BORDER = f"rgba({INK}, 0.12)"
    ROW_LINE = f"rgba({INK}, 0.06)"
    STYLESHEET = full_stylesheet()
    try:
        from PySide6.QtWidgets import QApplication

        app = QApplication.instance()
        if app is not None:
            app.setStyleSheet(STYLESHEET)
    except ImportError:
        pass
    return name
