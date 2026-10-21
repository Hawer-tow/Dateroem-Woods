"""The modern look's painting (21 October 2026, see theme.MODERN).

"look at how instead of a plain background green they have like a green that
has areas that have changed ... for the black theme ... shades of black that
gives a modern look in the menu and make it look like a droplet on glass on a
rainy day".

- Backdrop: the main window's background - a glow in one corner fading into
  the theme's darkest shade, darkening towards the far corner, with a fine
  grain so the gradient never shows bands. Painted once per window size.
- NavPane: the menu's column. In Dark it is a pane of dark glass with still
  raindrops on it (no animation - a work tool, and this PC keeps Windows'
  animations off); in Forest and Light the menu floats on the backdrop.
- NavDelegate: menu entries as Windows 11 draws them - a soft pill under the
  hovered or chosen one and a short wood bar beside the chosen one; icon only
  when the menu is folded.
"""
import random

from PySide6.QtCore import QPointF, QRectF, QSize, Qt
from PySide6.QtGui import QBrush, QColor, QFont, QImage, QLinearGradient, QPainter, QPixmap, QRadialGradient
from PySide6.QtWidgets import QStyle, QStyledItemDelegate, QWidget

from app.ui import theme

# Per theme: the vertical base (top to bottom), glows (centre as a fraction of
# the window - outside it is fine -, radius as a fraction of the longer side,
# RGBA colour at the centre) and how strong the grain is.
BACKDROPS = {
    "forest": {
        "base": [(0.0, "#163a2a"), (0.5, "#0f241b"), (1.0, "#08140e")],
        "glows": [((0.10, -0.12), 0.95, (62, 142, 99, 120)),
                  ((0.92, -0.10), 0.42, (235, 154, 75, 12)),
                  ((1.05, 1.15), 0.75, (0, 0, 0, 110))],
        "grain": 9,
    },
    "dark": {
        "base": [(0.0, "#202328"), (0.55, "#141619"), (1.0, "#0b0c0e")],
        "glows": [((0.08, -0.12), 0.9, (140, 160, 185, 34)),
                  ((1.05, 1.15), 0.75, (0, 0, 0, 120))],
        "grain": 8,
    },
    "light": {
        "base": [(0.0, "#fbf8f1"), (0.55, "#f4f0e6"), (1.0, "#ebe5d8")],
        "glows": [((0.08, -0.12), 0.9, (46, 120, 84, 26)),
                  ((0.95, -0.08), 0.5, (235, 154, 75, 20))],
        "grain": 5,
    },
}

_grain_tiles: dict[int, QPixmap] = {}
_backdrop_cache: dict[tuple, QPixmap] = {}
_rain_cache: dict[tuple, QPixmap] = {}


def _grain_tile(strength: int) -> QPixmap:
    """A 128 px tile of fine light-and-dark noise, the same every time."""
    tile = _grain_tiles.get(strength)
    if tile is None:
        rng = random.Random(7)
        image = QImage(128, 128, QImage.Format.Format_ARGB32_Premultiplied)
        image.fill(Qt.GlobalColor.transparent)
        for y in range(128):
            for x in range(128):
                v = rng.random()
                if v < 0.5:
                    image.setPixelColor(x, y, QColor(255, 255, 255, int(strength * (0.5 - v) * 2)))
                else:
                    image.setPixelColor(x, y, QColor(0, 0, 0, int(strength * (v - 0.5) * 2)))
        tile = QPixmap.fromImage(image)
        _grain_tiles[strength] = tile
    return tile


def backdrop_pixmap(width: int, height: int, ratio: float = 1.0) -> QPixmap:
    """The current theme's backdrop at this size (kept until the size or theme changes)."""
    key = (theme.CURRENT, width, height, ratio)
    pixmap = _backdrop_cache.get(key)
    if pixmap is not None:
        return pixmap
    spec = BACKDROPS.get(theme.CURRENT, BACKDROPS["forest"])
    pixmap = QPixmap(max(1, round(width * ratio)), max(1, round(height * ratio)))
    pixmap.setDevicePixelRatio(ratio)
    p = QPainter(pixmap)
    p.setRenderHint(QPainter.RenderHint.Antialiasing)
    base = QLinearGradient(0, 0, width * 0.25, height)
    for stop, colour in spec["base"]:
        base.setColorAt(stop, QColor(colour))
    p.fillRect(QRectF(0, 0, width, height), base)
    longest = max(width, height)
    for (cx, cy), radius, rgba in spec["glows"]:
        glow = QRadialGradient(QPointF(cx * width, cy * height), radius * longest)
        glow.setColorAt(0.0, QColor(*rgba))
        glow.setColorAt(1.0, QColor(rgba[0], rgba[1], rgba[2], 0))
        p.fillRect(QRectF(0, 0, width, height), glow)
    p.fillRect(QRectF(0, 0, width, height), QBrush(_grain_tile(spec["grain"])))
    p.end()
    _backdrop_cache.clear()
    _backdrop_cache[key] = pixmap
    return pixmap


class Backdrop(QWidget):
    """The main window's central widget: paints the backdrop under everything."""

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setObjectName("appBackdrop")
        self.setAttribute(Qt.WidgetAttribute.WA_OpaquePaintEvent)

    def paintEvent(self, _event):
        p = QPainter(self)
        p.drawPixmap(0, 0, backdrop_pixmap(self.width(), self.height(), self.devicePixelRatioF()))
        p.end()


def _rain_pixmap(width: int, height: int, ratio: float) -> QPixmap:
    """Still raindrops on dark glass: a few hundred drops of different sizes,
    each a little lens - a soft shadow below, a brighter rim, a pin of light -
    and some short trails where drops ran. Thinner over the middle of the
    column, where the menu's words are."""
    key = (width, height, ratio)
    pixmap = _rain_cache.get(key)
    if pixmap is not None:
        return pixmap
    pixmap = QPixmap(max(1, round(width * ratio)), max(1, round(height * ratio)))
    pixmap.setDevicePixelRatio(ratio)
    pixmap.fill(Qt.GlobalColor.transparent)
    p = QPainter(pixmap)
    p.setRenderHint(QPainter.RenderHint.Antialiasing)
    p.setPen(Qt.PenStyle.NoPen)
    rng = random.Random(21)
    count = int(width * height / 420)
    for _ in range(count):
        x = rng.uniform(0, width)
        y = rng.uniform(0, height)
        # Fewer and smaller drops across the words' band.
        in_text_band = width * 0.18 < x < width * 0.92
        if in_text_band and rng.random() < 0.78:
            continue
        big = rng.random() < (0.05 if in_text_band else 0.12)
        r = rng.uniform(3.5, 7.5) if big else rng.uniform(0.8, 2.6)
        if r < 1.6:
            p.setBrush(QColor(255, 255, 255, rng.randint(18, 40)))
            p.drawEllipse(QPointF(x, y), r * 0.6, r * 0.6)
            continue
        p.setBrush(QColor(0, 0, 0, 55))
        p.drawEllipse(QPointF(x + r * 0.25, y + r * 0.45), r, r * 1.05)
        lens = QRadialGradient(QPointF(x - r * 0.2, y - r * 0.25), r * 1.1)
        lens.setColorAt(0.0, QColor(255, 255, 255, 8))
        lens.setColorAt(0.75, QColor(255, 255, 255, 18))
        lens.setColorAt(1.0, QColor(255, 255, 255, 46))
        p.setBrush(QBrush(lens))
        p.drawEllipse(QPointF(x, y), r, r * 1.05)
        p.setBrush(QColor(255, 255, 255, 120 if big else 80))
        p.drawEllipse(QPointF(x - r * 0.35, y - r * 0.4), r * 0.28, r * 0.22)
        if big and rng.random() < 0.45:
            trail = QLinearGradient(x, y - r * 8, x, y)
            trail.setColorAt(0.0, QColor(255, 255, 255, 0))
            trail.setColorAt(1.0, QColor(255, 255, 255, 22))
            p.setBrush(QBrush(trail))
            p.drawRoundedRect(QRectF(x - r * 0.22, y - r * 8, r * 0.44, r * 8), r * 0.2, r * 0.2)
    p.end()
    _rain_cache.clear()
    _rain_cache[key] = pixmap
    return pixmap


class NavPane(QWidget):
    """The menu's column. Dark: a pane of dark glass with raindrops, its
    right edge catching a little light. Forest and Light: nothing - the
    menu floats on the backdrop."""

    def paintEvent(self, _event):
        if theme.CURRENT != "dark":
            return
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        rect = QRectF(self.rect()).adjusted(6, 6, -2, -6)
        glass = QLinearGradient(rect.topLeft(), rect.bottomRight())
        glass.setColorAt(0.0, QColor(255, 255, 255, 16))
        glass.setColorAt(1.0, QColor(255, 255, 255, 6))
        p.setPen(QColor(255, 255, 255, 18))
        p.setBrush(QBrush(glass))
        p.drawRoundedRect(rect, 14, 14)
        p.setClipRect(rect.adjusted(1, 1, -1, -1))
        p.drawPixmap(rect.topLeft(), _rain_pixmap(int(rect.width()), int(rect.height()), self.devicePixelRatioF()))
        p.end()


class NavDelegate(QStyledItemDelegate):
    """Menu entries the Windows 11 way: a soft pill under the hovered or
    chosen entry, a short wood bar beside the chosen one, the icon, and the
    name (left out when the menu is folded to icons)."""

    def __init__(self, parent=None):
        super().__init__(parent)
        self.folded = False

    def sizeHint(self, option, index):
        return QSize(option.rect.width(), 40)

    def paint(self, painter, option, index):
        painter.save()
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        rect = QRectF(option.rect).adjusted(4, 2, -4, -2)
        selected = bool(option.state & QStyle.StateFlag.State_Selected)
        hovered = bool(option.state & QStyle.StateFlag.State_MouseOver)
        ink = QColor(0, 0, 0) if theme.IS_LIGHT else QColor(255, 255, 255)
        if selected or hovered:
            ink.setAlpha(26 if selected else 15)
            painter.setPen(Qt.PenStyle.NoPen)
            painter.setBrush(ink)
            painter.drawRoundedRect(rect, 8, 8)
        if selected:
            painter.setBrush(QColor(theme.WOOD if not theme.IS_LIGHT else theme.ACCENT))
            painter.drawRoundedRect(QRectF(rect.left() + 1, rect.center().y() - 8, 3, 16), 1.5, 1.5)
        icon = index.data(Qt.ItemDataRole.DecorationRole)
        icon_size = 18
        if self.folded:
            icon_rect = QRectF(rect.center().x() - icon_size / 2, rect.center().y() - icon_size / 2, icon_size, icon_size)
        else:
            icon_rect = QRectF(rect.left() + 12, rect.center().y() - icon_size / 2, icon_size, icon_size)
        if icon is not None:
            mode = icon.Mode.Selected if selected else icon.Mode.Normal
            icon.paint(painter, icon_rect.toRect(), Qt.AlignmentFlag.AlignCenter, mode)
        if not self.folded:
            font = QFont(option.font)
            font.setWeight(QFont.Weight.DemiBold if selected else QFont.Weight.Normal)
            painter.setFont(font)
            painter.setPen(QColor(theme.TEXT if (selected or hovered) else theme.TEXT_MUTED))
            text_rect = rect.adjusted(icon_rect.right() - rect.left() + 10, 0, -6, 0)
            painter.drawText(text_rect, Qt.AlignmentFlag.AlignVCenter | Qt.AlignmentFlag.AlignLeft,
                             index.data(Qt.ItemDataRole.DisplayRole) or "")
        painter.restore()
