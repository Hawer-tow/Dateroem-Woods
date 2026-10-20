"""The entrance pictures and how they are drawn - shared by the intro
(splash_screen.py) and the sign-in screen (login_window.py), so sign-in
carries on exactly where the intro ends: the same misty forest in the same
place on the screen, the same drifting mist, the same green wash ("let it
feel like a transition graceful so that even when reaching the login page
it feels like we are on point in theme", 19 October 2026).

Pictures are scaled once per screen size and then copied unscaled each
frame (cheap); positions are snapped to whole device pixels.
"""
import math

from PySide6.QtCore import QPointF, QRectF, Qt
from PySide6.QtGui import QBrush, QColor, QImage, QLinearGradient, QPainter, QPixmap, QRadialGradient, QTransform

from app.ui import theme

# Where the forest settles: the point of the photo (0 top .. 1 bottom) in
# the middle of the screen when the intro ends - and behind sign-in.
FOREST_REST = 0.57
# When the intro ends - and so where sign-in's mist carries on from.
MIST_AT_END_MS = 4400

SHADE = (0x05, 0x0B, 0x07)


def progress(t: float, start: float, duration: float) -> float:
    """0..1 through [start, start + duration], eased in and out."""
    x = min(1.0, max(0.0, (t - start) / duration))
    return x * x * (3 - 2 * x)


class EntrancePictures:
    """The entrance photos and the mist texture for one widget, with its scaled copies."""

    def __init__(self, widget):
        self.widget = widget
        self.photos = {"forest": QPixmap(theme.ENTRANCE_FOREST)}
        self.fog = QPixmap(theme.ENTRANCE_FOG)
        self._covers = {}
        self._fog_tiles = {}
        self._puffs = {}

    def mist_puff(self, w: float, h: float) -> QPixmap:
        """A soft cloud of the mist, w x h, thinning out toward its edges -
        what the sign-in card turns into when Manage Servers is pressed, and
        what the servers card comes out of (login_window._MistSwap)."""
        dpr = self._dpr()
        pw, ph = round(w * dpr), round(h * dpr)
        key = (pw, ph, dpr)
        puff = self._puffs.get(key)
        if puff is None:
            img = QImage(pw, ph, QImage.Format.Format_ARGB32_Premultiplied)
            img.fill(Qt.GlobalColor.transparent)
            p = QPainter(img)
            p.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform)
            # A faint glow of mist, with two layers of the mist texture over it ...
            glow = QRadialGradient(QPointF(pw / 2, ph / 2), max(pw, ph) / 2)
            glow.setColorAt(0.0, QColor(226, 232, 228, 110))
            glow.setColorAt(1.0, QColor(226, 232, 228, 0))
            p.fillRect(img.rect(), glow)
            if not self.fog.isNull():
                fog = self.fog.toImage()
                p.drawImage(QRectF(0, ph * 0.05, pw, ph * 0.90), fog)
                p.drawImage(QRectF(0, ph * 0.15, pw, ph * 0.75), fog.mirrored(True, True))
            # ... thinning out toward the edges of an oval.
            p.setCompositionMode(QPainter.CompositionMode.CompositionMode_DestinationIn)
            p.translate(pw / 2, ph / 2)
            p.scale(pw / 2, ph / 2)
            mask = QRadialGradient(QPointF(0, 0), 1.0)
            mask.setColorAt(0.0, QColor(0, 0, 0, 255))
            mask.setColorAt(0.55, QColor(0, 0, 0, 190))
            mask.setColorAt(1.0, QColor(0, 0, 0, 0))
            p.fillRect(QRectF(-1, -1, 2, 2), mask)
            p.end()
            puff = QPixmap.fromImage(img)
            puff.setDevicePixelRatio(dpr)
            self._puffs[key] = puff
        return puff

    def _dpr(self) -> float:
        return self.widget.devicePixelRatioF()

    def snap(self, value: float) -> float:
        dpr = self._dpr()
        return round(value * dpr) / dpr

    def cover(self, name: str, w: int, h: int, oversize: float = 1.0) -> QPixmap:
        """The photo scaled to cover a w x h frame (times `oversize`)."""
        photo = self.photos[name]
        dpr = self._dpr()
        key = (name, w, h, oversize, dpr)
        if key not in self._covers:
            fit = max(w / photo.width(), h / photo.height()) * oversize * dpr
            pix = photo.scaled(
                round(photo.width() * fit), round(photo.height() * fit),
                Qt.AspectRatioMode.IgnoreAspectRatio, Qt.TransformationMode.SmoothTransformation,
            )
            pix.setDevicePixelRatio(dpr)
            self._covers[key] = pix
        return self._covers[key]

    def fog_tile(self, height: float, stretch: float, flip: str = '') -> QPixmap:
        """The mist texture scaled for one band, ready to repeat side by side.

        Scaled as three copies in a row with only the middle one kept, so its
        left and right edges blend into its neighbours: the repeats join
        without a seam ("i can see some straight lines ... also moving
        horizontally", 20 October 2026 - the copies used to overlap by a
        pixel, doubling the mist into a thin bright line at every join)."""
        dpr = self._dpr()
        th = round(height * dpr)
        tw = round(self.fog.width() * th / self.fog.height() * stretch)
        key = (th, tw, flip, dpr)
        tile = self._fog_tiles.get(key)
        if tile is None:
            source = self.fog.toImage()
            if flip:   # 'h' left-right, 'hv' also upside down - different shapes per layer
                source = source.mirrored('h' in flip, 'v' in flip)
            strip = QImage(source.width() * 3, source.height(), QImage.Format.Format_ARGB32_Premultiplied)
            strip.fill(Qt.GlobalColor.transparent)
            painter = QPainter(strip)
            for i in range(3):
                painter.drawImage(i * source.width(), 0, source)
            painter.end()
            scaled = strip.scaled(tw * 3, th, Qt.AspectRatioMode.IgnoreAspectRatio, Qt.TransformationMode.SmoothTransformation)
            tile = QPixmap.fromImage(scaled.copy(tw, 0, tw, th))
            tile.setDevicePixelRatio(dpr)
            self._fog_tiles[key] = tile
        return tile

    def draw_photo(self, p: QPainter, name: str, frame: QRectF, centre_y: float, alpha: float = 1.0):
        """The photo covering `frame`, its point `centre_y` in the frame's
        middle (never past its edges). An unscaled copy: cheap to slide."""
        photo = self.photos[name]
        if alpha <= 0 or photo.isNull():
            return
        pix = self.cover(name, round(frame.width()), round(frame.height()))
        pw = pix.width() / pix.devicePixelRatio()
        ph = pix.height() / pix.devicePixelRatio()
        x = frame.x() + (frame.width() - pw) / 2
        y = frame.y() + min(0.0, max(frame.height() - ph, frame.height() / 2 - centre_y * ph))
        p.setOpacity(min(alpha, 1.0))
        p.drawPixmap(QPointF(self.snap(x), self.snap(y)), pix)
        p.setOpacity(1.0)

    def draw_fog(self, p: QPainter, t: float, frame: QRectF, centre_y: float, band: float, alpha: float,
                 speed: float, stretch: float = 1.6, flip: str = ''):
        """One band of mist drifting left across `frame`: `band` of its height
        tall, centred at `centre_y`; `speed` is the phone's dp per second.
        Filled with the tile as a repeating texture - copies meet edge to
        edge, never overlapping."""
        if alpha <= 0 or self.fog.isNull():
            return
        dpr = self._dpr()
        height = frame.height() * band
        tile = self.fog_tile(height, stretch, flip)
        tile_w = tile.width() / dpr
        travelled = (t / 1000) * speed * frame.height() / 800
        top = self.snap(frame.y() + frame.height() * centre_y - height / 2)
        brush = QBrush(tile)
        brush.setTransform(QTransform.fromTranslate(self.snap(frame.x() - (travelled % tile_w)), top))
        p.setOpacity(min(alpha, 1.0))
        p.fillRect(QRectF(frame.x(), top, frame.width(), tile.height() / dpr), brush)
        p.setOpacity(1.0)

    def prepare_fog(self, frame_h: float, bands):
        """Scales the mist for every band before anything is drawn ((band, stretch[, flip]) each)."""
        for band, stretch, *flip in bands:
            self.fog_tile(frame_h * band, stretch, flip[0] if flip else '')

    def draw_forest_at_rest(self, p: QPainter, t: float, frame: QRectF):
        """The forest where the intro leaves it, with its resting mist."""
        self.draw_photo(p, "forest", frame, FOREST_REST)
        self.draw_resting_mist(p, t, frame)

    def draw_resting_mist(self, p: QPainter, t: float, frame: QRectF, alpha: float = 1.0):
        """The mist over the forest's tree line, as real mist moves ("allow it
        to feel even more real not too much just perfect", 20 October 2026):
        three layers at different depths - the far one widest, faintest and
        slowest, the near one quicker - each slowly thickening and thinning
        and rising and settling a little, out of step with the others."""
        s = t / 1000
        for i, (centre, band, strength, speed, stretch, flip, breath, drift, phase) in enumerate(MIST_LAYERS):
            breathe = 0.80 + 0.20 * math.sin(2 * math.pi * s / breath + phase)
            rise = 0.008 * math.sin(2 * math.pi * s / drift + phase * 1.7)
            self.draw_fog(p, t + i * 7000, frame, centre + rise, band, strength * breathe * alpha, speed, stretch, flip)


# The resting mist's layers: centre and height (fractions of the screen),
# strength, drift speed (the phone's dp per second), how wide the texture is
# stretched (wide, so its repeats are far apart), how it is flipped (so no
# two layers show the same shapes), and the seconds each takes to thicken
# and thin, and to rise and settle.
# Stronger and a little quicker since 20 October 2026 ("make it more visible").
MIST_LAYERS = (
    (0.38, 0.50, 0.34, 7, 2.6, 'h', 23.0, 31.0, 0.0),     # far: a wide, soft bank
    (0.44, 0.42, 0.64, 13, 1.6, '', 17.0, 27.0, 1.7),     # middle
    (0.58, 0.30, 0.40, 20, 1.9, 'hv', 13.0, 19.0, 3.1),   # near: thinner wisps, quicker
)
RESTING_MIST_BANDS = tuple((band, stretch, flip) for _, band, _, _, stretch, flip, *_ in MIST_LAYERS)


def draw_scrim(p: QPainter, frame: QRectF, strength: float, area: QRectF | None = None):
    """Darkens the photo under the brand: deeper at the top and bottom, lighter
    through the middle - laid out over `frame`, filling `area` (default: frame)."""
    if strength <= 0:
        return
    g = QLinearGradient(0, frame.top(), 0, frame.bottom())
    g.setColorAt(0.0, QColor(*SHADE, int(255 * min(1.0, 0.85 * strength))))
    g.setColorAt(0.45, QColor(*SHADE, int(255 * 0.55 * strength)))
    g.setColorAt(1.0, QColor(*SHADE, int(255 * min(1.0, 0.95 * strength))))
    p.fillRect(area or frame, g)


def draw_green_wash(p: QPainter, frame: QRectF, strength: float, area: QRectF | None = None):
    """The app's own greens washing over the forest - the colours of every
    screen after sign-in - so the photo and the app meet halfway."""
    if strength <= 0:
        return
    top, mid, bottom = QColor(theme.BACKGROUND), QColor(theme.BACKGROUND), QColor(theme.SURFACE)
    top.setAlphaF(0.55 * strength)
    mid.setAlphaF(0.30 * strength)
    bottom.setAlphaF(0.62 * strength)
    g = QLinearGradient(0, frame.top(), 0, frame.bottom())
    g.setColorAt(0.0, top)
    g.setColorAt(0.5, mid)
    g.setColorAt(1.0, bottom)
    p.fillRect(area or frame, g)


def draw_forest_backdrop(pictures: EntrancePictures, p: QPainter, t: float, frame: QRectF, area: QRectF):
    """Sign-in's backdrop - the intro's final picture without the brand:
    the resting forest and mist, the brand's darkening, and the green wash.
    Laid out over `frame` (the whole screen); shades all of `area` (the
    window), even where the window reaches past the screen's edge."""
    p.fillRect(area, QColor(theme.BACKGROUND))
    pictures.draw_forest_at_rest(p, t, frame)
    draw_scrim(p, frame, 0.62, area)
    draw_green_wash(p, frame, 1.0, area)
