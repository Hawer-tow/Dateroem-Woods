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


STYLESHEET = build_stylesheet()


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
    STYLESHEET = build_stylesheet()
    try:
        from PySide6.QtWidgets import QApplication

        app = QApplication.instance()
        if app is not None:
            app.setStyleSheet(STYLESHEET)
    except ImportError:
        pass
    return name
