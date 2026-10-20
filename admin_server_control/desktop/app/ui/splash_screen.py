"""Animated intro - the phone app's entrance, made for a wide screen (19
October 2026: "the admins have reviewed it as a better entry"): the misty
pine forest rises out of the dark under a veil of mist, the mist clears
while the camera slowly rises, and the company emblem, the wood wordmark and
the tagline settle in. A click or any key skips it.

The phone opens on the old oak; the desktop does not ("lets do away with the
first image"): the oak photo is tall, and a wide screen can only show it
either cropped or with empty sides. The misty forest fills the screen.

It hands over to sign-in without a jump ("let it feel like a transition
graceful"): in its last moments the app's own greens wash over the forest,
then the sign-in screen - the same forest in the same place, the same mist
and the same wash (entrance_paint.draw_forest_backdrop) - opens behind it
and the intro dissolves into it, the brand giving way to the sign-in card.
Every picture is scaled once, before the clock starts; the picture comes
from brand/entrance photos/prepare_entrance.py --desktop."""
import time

from PySide6.QtCore import QEasingCurve, QPointF, QRectF, Qt, QTimer, QVariantAnimation
from PySide6.QtGui import QColor, QFont, QFontMetrics, QPainter, QPixmap, QRadialGradient
from PySide6.QtWidgets import QWidget

from app.ui import theme
from app.ui.entrance_paint import (
    FOREST_REST,
    MIST_AT_END_MS,
    RESTING_MIST_BANDS,
    SHADE,
    EntrancePictures,
    draw_green_wash,
    draw_scrim,
    progress,
)

# The entrance, in milliseconds from the first frame.
FOREST_RISE_MS = 1100       # the forest and its mist rise out of the dark
MIST_CLEARS = 900           # ... the veil of mist clears
MIST_CLEAR_MS = 1300
CAMERA_RISE_MS = 3600       # the camera rising slowly to where sign-in finds it
BRAND_SCRIM = 1900
EMBLEM_IN = 2100
WORDMARK_IN = 2600
TAGLINE_IN = 3050
GREEN_WASH = 3500           # the app's greens wash in, as behind sign-in
GREEN_WASH_MS = 900
TOTAL_MS = MIST_AT_END_MS   # 4400; sign-in's mist carries on from here
DISSOLVE_MS = 750           # the intro dissolving into the sign-in screen
SKIP_DISSOLVE_MS = 350

TAGLINE = "Growing Timber, Growing Futures"
APP_NAME = "Admin Control"

VEIL = QColor(0x8A, 0x96, 0x8F)
VEIL_BANDS = ((0.9, 1.6), (0.8, 1.6))


class SplashScreen(QWidget):
    def __init__(self, on_finish, on_closed=None, parent=None):
        super().__init__(parent)
        self.on_finish = on_finish      # opens the sign-in screen behind the intro
        self.on_closed = on_closed      # once the intro has dissolved away
        self._finished = False
        # Stays above the sign-in screen opening behind it, until it has dissolved.
        self.setWindowFlags(Qt.WindowType.FramelessWindowHint | Qt.WindowType.WindowStaysOnTopHint)

        self._pics = EntrancePictures(self)
        self._emblem = QPixmap(theme.EMBLEM)
        self._wordmark = QPixmap(theme.WORDMARK)
        self._brand = {}        # (name, size) -> emblem / wordmark scaled once
        self._dissolve = None

        # The clock starts at the first frame, once the pictures are scaled,
        # so the first thing seen is the forest rising - not a photo popping in.
        self._start = None
        self._timer = QTimer(self)
        self._timer.timeout.connect(self._tick)
        self._timer.start(16)
        # Never stuck on a dark screen, whatever happens (counted from the
        # first frame too, once it has been drawn - see paintEvent).
        QTimer.singleShot(TOTAL_MS + 10000, self._finish)

    # "allow one to navigate without getting blocked" - a click or any key
    # skips the rest of the intro.
    def mousePressEvent(self, _event):
        self._finish(SKIP_DISSOLVE_MS)

    def keyPressEvent(self, _event):
        self._finish(SKIP_DISSOLVE_MS)

    def _elapsed_ms(self) -> float:
        return 0.0 if self._start is None else (time.monotonic() - self._start) * 1000

    def _tick(self):
        if not self._finished and self._start is not None and self._elapsed_ms() >= TOTAL_MS:
            self._finish()
        self.update()

    def _finish(self, dissolve_ms: int = DISSOLVE_MS):
        if self._finished:
            return
        self._finished = True
        # The sign-in screen opens behind this one (shown *before* this one
        # closes - QApplication quits on its last visible window closing),
        # then this one dissolves into it, still animating its mist.
        self.on_finish()
        self.raise_()
        anim = QVariantAnimation(self)
        anim.setStartValue(1.0)
        anim.setEndValue(0.0)
        anim.setDuration(dissolve_ms)
        anim.setEasingCurve(QEasingCurve.Type.InOutSine)
        anim.valueChanged.connect(lambda v: self.setWindowOpacity(float(v)))
        anim.finished.connect(self._close)
        self._dissolve = anim   # kept referenced, or Python collects it mid-way
        anim.start()

    def _close(self):
        self._timer.stop()
        self.close()
        if self.on_closed is not None:
            self.on_closed()

    # ---- pictures, scaled once per screen size ---------------------------------

    def _scaled_brand(self, name: str, source: QPixmap, width: float) -> QPixmap:
        dpr = self.devicePixelRatioF()
        key = (name, round(width), dpr)
        if key not in self._brand:
            pix = source.scaledToWidth(round(width * dpr), Qt.TransformationMode.SmoothTransformation)
            pix.setDevicePixelRatio(dpr)
            self._brand[key] = pix
        return self._brand[key]

    def _brand_layout(self, w: int, h: int):
        """Emblem size, wordmark width and height, gap, tagline and app-name pixel sizes."""
        emblem_size = min(h * 0.27, w * 0.30)
        wordmark_w = emblem_size * 1.6
        wordmark_h = (wordmark_w * self._wordmark.height() / self._wordmark.width()) if not self._wordmark.isNull() else 0
        return emblem_size, wordmark_w, wordmark_h, h * 0.02, max(12, round(h * 0.018)), max(10, round(h * 0.014))

    def _prepare(self, w: int, h: int):
        """Everything the intro will draw, scaled for this screen before the
        clock starts - so no frame stalls while a picture is resized."""
        pics = self._pics
        if not pics.photos["forest"].isNull():
            pics.cover("forest", w, h)
        if not pics.fog.isNull():
            pics.prepare_fog(h, RESTING_MIST_BANDS + VEIL_BANDS)
        emblem_size, wordmark_w, _, _, tagline_px, name_px = self._brand_layout(w, h)
        if not self._emblem.isNull():
            self._scaled_brand("emblem", self._emblem, emblem_size)
        if not self._wordmark.isNull():
            self._scaled_brand("wordmark", self._wordmark, wordmark_w)
        for px in (tagline_px, name_px):
            font = QFont("Segoe UI")
            font.setPixelSize(px)
            QFontMetrics(font).horizontalAdvance(TAGLINE + APP_NAME)

    # ---- drawing -----------------------------------------------------------------

    def paintEvent(self, _event):
        p = QPainter(self)
        w, h = self.width(), self.height()
        frame = QRectF(0, 0, w, h)
        p.fillRect(self.rect(), QColor(0x07, 0x08, 0x0A))
        if self._start is None:
            self._prepare(w, h)
            self._start = time.monotonic()
            QTimer.singleShot(TOTAL_MS + 2500, self._finish)
        t = self._elapsed_ms()
        pics = self._pics

        # The forest rising out of the dark under a veil of mist, the camera
        # slowly rising to where sign-in will find it.
        rise = progress(t, 0, FOREST_RISE_MS)
        camera = progress(t, 0, CAMERA_RISE_MS)
        pics.draw_photo(p, "forest", frame, FOREST_REST + 0.07 * (1 - camera), rise)
        pics.draw_resting_mist(p, t, frame, rise)
        # The veil: thick, fast banks of mist over a soft grey-green, clearing.
        veil = rise * (1 - progress(t, MIST_CLEARS, MIST_CLEAR_MS))
        if veil > 0:
            p.fillRect(self.rect(), QColor(VEIL.red(), VEIL.green(), VEIL.blue(), int(255 * 0.88 * veil)))
            pics.draw_fog(p, t * 2.5, frame, 0.40, 0.9, veil, 24)
            pics.draw_fog(p, t * 2.5 + 3000, frame, 0.70, 0.8, veil, 36)

        scrim = progress(t, BRAND_SCRIM, 1000)
        draw_scrim(p, frame, 0.62 * scrim)
        # The app's greens washing in - sign-in's colours, so the hand-over is seamless.
        draw_green_wash(p, frame, progress(t, GREEN_WASH, GREEN_WASH_MS))
        self._draw_brand(p, w, h, t, scrim)

    def _draw_brand(self, p: QPainter, w: int, h: int, t: float, scrim: float):
        emblem_in = progress(t, EMBLEM_IN, 800)
        wordmark_in = progress(t, WORDMARK_IN, 600)
        tagline_in = progress(t, TAGLINE_IN, 550)

        # The emblem, wordmark and tagline as one block, a little above the middle.
        emblem_size, wordmark_w, wordmark_h, gap, tagline_px, name_px = self._brand_layout(w, h)
        block = emblem_size + gap + wordmark_h + gap * 0.4 + tagline_px * 1.5 + name_px * 1.6
        top = h * 0.47 - block / 2
        emblem_centre = QPointF(w / 2, top + emblem_size / 2)

        # A soft shade behind the emblem so its gold reads against the mist.
        if scrim > 0:
            shade = QRadialGradient(QPointF(w / 2, h * 0.42), min(w, h) * 0.75)
            shade.setColorAt(0.0, QColor(*SHADE, int(255 * 0.45 * scrim)))
            shade.setColorAt(1.0, QColor(*SHADE, 0))
            p.fillRect(0, 0, w, h, shade)

        p.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        if emblem_in > 0 and not self._emblem.isNull():
            p.setOpacity(emblem_in)
            # A warm glow from the wood, a little wider than the emblem.
            glow_r = emblem_size * 0.75
            glow = QRadialGradient(emblem_centre, glow_r)
            accent = QColor(theme.WOOD)
            glow.setColorAt(0.0, QColor(accent.red(), accent.green(), accent.blue(), int(255 * 0.20)))
            glow.setColorAt(1.0, QColor(accent.red(), accent.green(), accent.blue(), 0))
            p.setPen(Qt.PenStyle.NoPen)
            p.setBrush(glow)
            p.drawEllipse(emblem_centre, glow_r, glow_r)
            emblem = self._scaled_brand("emblem", self._emblem, emblem_size)
            s = 0.86 + 0.14 * emblem_in
            size = emblem_size * s
            p.drawPixmap(QRectF(emblem_centre.x() - size / 2, emblem_centre.y() - size / 2, size, size), emblem,
                         QRectF(emblem.rect()))
            p.setOpacity(1.0)

        y = top + emblem_size + gap
        if wordmark_in > 0 and not self._wordmark.isNull():
            wordmark = self._scaled_brand("wordmark", self._wordmark, wordmark_w)
            p.setOpacity(wordmark_in)
            rise = h * 0.012 * (1 - wordmark_in)
            p.drawPixmap(QPointF(self._pics.snap(w / 2 - wordmark_w / 2), self._pics.snap(y + rise)), wordmark)
            p.setOpacity(1.0)
        y += wordmark_h + gap * 0.4

        if tagline_in > 0:
            font = QFont("Segoe UI")
            font.setPixelSize(tagline_px)
            font.setLetterSpacing(QFont.SpacingType.AbsoluteSpacing, tagline_px * 0.08)
            p.setFont(font)
            muted = QColor(theme.TEXT_MUTED)
            muted.setAlphaF(tagline_in)
            p.setPen(muted)
            p.drawText(QRectF(0, y, w, tagline_px * 1.5), Qt.AlignmentFlag.AlignHCenter | Qt.AlignmentFlag.AlignTop, TAGLINE)
            y += tagline_px * 1.5
            font = QFont("Segoe UI")
            font.setPixelSize(name_px)
            font.setLetterSpacing(QFont.SpacingType.AbsoluteSpacing, name_px * 0.12)
            p.setFont(font)
            wood = QColor(theme.WOOD)
            wood.setAlphaF(tagline_in)
            p.setPen(wood)
            p.drawText(QRectF(0, y + name_px * 0.3, w, name_px * 1.6), Qt.AlignmentFlag.AlignHCenter | Qt.AlignmentFlag.AlignTop,
                       APP_NAME.upper())
