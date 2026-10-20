"""Shared forest/timber drawing helpers - the pine-tree silhouette (same
trunk + 3-canopy-triangle proportions as the mobile app's drawPineTree())
and a sawn-log cross-section, used by both the splash screen and the login
page's background scene so the two share one consistent art style instead
of duplicating the geometry."""
from dataclasses import dataclass

from PySide6.QtCore import QEasingCurve, Qt
from PySide6.QtGui import QBrush, QColor, QPainter, QPainterPath, QPen


def ease(curve_type: QEasingCurve.Type, t: float) -> float:
    t = max(0.0, min(1.0, t))
    return QEasingCurve(curve_type).valueForProgress(t)


@dataclass
class BackgroundTree:
    x_fraction: float
    scale: float
    delay_ms: int
    color: QColor


BACKGROUND_TREES = [
    BackgroundTree(0.10, 0.50, 0, QColor(0x1F, 0x4A, 0x2C)),
    BackgroundTree(0.22, 0.70, 90, QColor(0x26, 0x5A, 0x34)),
    BackgroundTree(0.36, 0.42, 180, QColor(0x1B, 0x40, 0x26)),
    BackgroundTree(0.64, 0.58, 60, QColor(0x26, 0x5A, 0x34)),
    BackgroundTree(0.79, 0.45, 150, QColor(0x1F, 0x4A, 0x2C)),
    BackgroundTree(0.91, 0.66, 240, QColor(0x1B, 0x40, 0x26)),
]


def draw_pine_tree(
    painter: QPainter,
    base_x: float,
    base_y: float,
    unit: float,
    growth: float,
    color: QColor,
    alpha: float = 1.0,
    trunk: bool = False,
):
    unit *= growth
    if unit <= 0:
        return
    if trunk:
        trunk_color = QColor(0x4A, 0x33, 0x20)
        trunk_color.setAlphaF(alpha)
        painter.fillRect(
            int(base_x - unit * 0.08), int(base_y - unit * 1.3), max(1, int(unit * 0.16)), int(unit * 1.3), trunk_color
        )
    for top, width in ((1.3, 2.2), (1.05, 1.85), (0.85, 1.5)):
        path = QPainterPath()
        path.moveTo(base_x, base_y - unit * top)
        path.lineTo(base_x - unit * width * 0.5, base_y - unit * (top - 0.85))
        path.lineTo(base_x + unit * width * 0.5, base_y - unit * (top - 0.85))
        path.closeSubpath()
        tone = QColor(color)
        tone.setAlphaF(alpha)
        painter.fillPath(path, QBrush(tone))


def draw_log_round(painter: QPainter, cx: float, cy: float, radius: float, alpha: float = 1.0):
    """A sawn log's end grain - concentric rings on a pale sapwood disc,
    the timber/sawmill counterpart to the pine-tree silhouettes."""
    bark = QColor(0x4A, 0x33, 0x20)
    bark.setAlphaF(alpha)
    wood = QColor(0xC9, 0xA9, 0x6E)
    wood.setAlphaF(alpha * 0.9)
    painter.setPen(QPen(bark, max(1.0, radius * 0.06)))
    painter.setBrush(QBrush(wood))
    painter.drawEllipse(int(cx - radius), int(cy - radius), int(radius * 2), int(radius * 2))

    ring_color = QColor(0x8A, 0x5A, 0x2E)
    ring_color.setAlphaF(alpha * 0.55)
    painter.setBrush(Qt.BrushStyle.NoBrush)
    painter.setPen(QPen(ring_color, max(1.0, radius * 0.035)))
    for frac in (0.35, 0.55, 0.75, 0.92):
        r = radius * frac
        painter.drawEllipse(int(cx - r), int(cy - r), int(r * 2), int(r * 2))
