"""The animated scene a payout shows while it is on its way.

A payment shouldn't feel like a spinner. In the forest theme: the company
wallet (a pine) hands leaf-coins along a moving track to the recipient, the
stages tick off underneath, and when it lands a ring blooms, the check draws
itself in and a burst of leaves scatters. Purely visual - PayoutDialog drives
it through set_sending() / set_success().
"""
import math
import time

from PySide6.QtCore import QPointF, QRectF, Qt, QTimer
from PySide6.QtGui import QColor, QFont, QPainter, QPainterPath, QPen
from PySide6.QtWidgets import QSizePolicy, QWidget

GREEN = QColor("#4FA35C")
LEAF = QColor("#9BD18B")
WHITE = QColor("#FFFFFF")

FLOW_SECONDS = 2.4


def _leaf_path(length: float) -> QPainterPath:
    path = QPainterPath()
    path.moveTo(-length / 2, 0)
    path.quadTo(0, -length * 0.5, length / 2, 0)
    path.quadTo(0, length * 0.5, -length / 2, 0)
    path.closeSubpath()
    return path


def _ease_out_back(t: float) -> float:
    c1, c3 = 1.70158, 2.70158
    return 1 + c3 * (t - 1) ** 3 + c1 * (t - 1) ** 2


class TransferScene(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent)
        # Tall enough for the amount, the track, all three stages and the
        # footer line (it used to be 250 and cut the last stages off).
        self.setFixedHeight(320)
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
        self.mode = "sending"  # "sending" | "success"
        self.amount = ""
        self.recipient = "Recipient"
        self.stage = 0  # 0 authorising, 1 handed to M-Pesa
        self.waited = 0
        self._t0 = time.monotonic()
        self._success_t0 = 0.0
        self._timer = QTimer(self)
        self._timer.timeout.connect(self.update)
        self.hide()

    # ---- driven by the dialog ------------------------------------------

    def set_sending(self, amount: str, recipient: str | None, stage: int, waited: int = 0):
        self.mode = "sending"
        self.amount = amount
        self.recipient = recipient or "Recipient"
        self.stage = stage
        self.waited = waited
        self._start()

    def set_success(self, amount: str, recipient: str | None):
        self.mode = "success"
        self.amount = amount
        self.recipient = recipient or "Recipient"
        self._success_t0 = time.monotonic()
        self._start()
        # Stop repainting once the burst has finished.
        QTimer.singleShot(1600, self._settle)

    def stop(self):
        self._timer.stop()
        self.hide()

    def _start(self):
        self.show()
        if not self._timer.isActive():
            self._timer.start(33)

    def _settle(self):
        if self.mode == "success":
            self._timer.stop()
            self.update()

    # ---- painting ------------------------------------------------------

    def paintEvent(self, _event):
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        text_color = self.palette().text().color()
        muted = QColor(text_color)
        muted.setAlpha(150)
        if self.mode == "success":
            self._paint_success(p, text_color)
        else:
            self._paint_sending(p, text_color, muted)

    def _paint_sending(self, p: QPainter, text_color: QColor, muted: QColor):
        w = self.width()
        phase = ((time.monotonic() - self._t0) % FLOW_SECONDS) / FLOW_SECONDS

        p.setPen(muted)
        f = QFont(self.font())
        f.setPointSize(9)
        f.setBold(True)
        p.setFont(f)
        p.drawText(QRectF(0, 4, w, 18), Qt.AlignmentFlag.AlignCenter, "SECURE TRANSFER")
        f.setPointSize(22)
        p.setFont(f)
        p.setPen(GREEN)
        p.drawText(QRectF(0, 22, w, 40), Qt.AlignmentFlag.AlignCenter, f"KES {self.amount}")

        y = 100.0
        left, right = QPointF(w * 0.13, y), QPointF(w * 0.87, y)

        # Moving dashed track.
        track = QPen(QColor(79, 163, 92, 95), 3, Qt.PenStyle.CustomDashLine)
        track.setDashPattern([4.5, 4.0])
        track.setDashOffset(-phase * 8.5 * 1.0)
        track.setCapStyle(Qt.PenCapStyle.RoundCap)
        p.setPen(track)
        p.drawLine(left, right)

        # Pulsing halos.
        pulse = 1.04 + 0.14 * math.sin(phase * 2 * math.pi)
        p.setPen(Qt.PenStyle.NoPen)
        p.setBrush(QColor(79, 163, 92, 38))
        p.drawEllipse(left, 34 * pulse, 34 * pulse)
        p.drawEllipse(right, 34 * (2.08 - pulse), 34 * (2.08 - pulse))
        p.setBrush(GREEN)
        p.drawEllipse(left, 26, 26)
        p.drawEllipse(right, 26, 26)
        self._draw_tree(p, left, 30)
        self._draw_person(p, right, 26)

        # Leaf-coins riding the track.
        span = right.x() - left.x() - 60
        for i in range(4):
            q = (phase + i / 4) % 1.0
            fade = math.sin(q * math.pi)
            x = left.x() + 30 + span * q
            lift = -fade * 16
            color = QColor(LEAF)
            color.setAlphaF(min(1.0, 0.35 + 0.65 * fade))
            self._draw_leaf(p, QPointF(x, y + lift - 2), 20, -20 + 40 * q, color)

        p.setPen(text_color)
        f.setPointSize(9)
        p.setFont(f)
        p.drawText(QRectF(left.x() - 60, y + 36, 120, 18), Qt.AlignmentFlag.AlignCenter, "Company wallet")
        p.drawText(QRectF(right.x() - 80, y + 36, 160, 18), Qt.AlignmentFlag.AlignCenter, self.recipient)

        # Stages.
        waiting = f"  ({self.waited}s)" if self.stage >= 1 and self.waited > 0 else ""
        stages = [
            ("Payment authorised", "done" if self.stage >= 1 else "active"),
            ("Sent to M-Pesa", "done" if self.stage >= 1 else "waiting"),
            ("Waiting for M-Pesa to confirm" + waiting, "active" if self.stage >= 1 else "waiting"),
        ]
        blink = 0.35 + 0.65 * (0.5 + 0.5 * math.sin(phase * 2 * math.pi * 2.4))
        sy = 168.0
        sx = w * 0.5 - 130
        f.setBold(False)
        f.setPointSize(10)
        p.setFont(f)
        for label, state in stages:
            c = QPointF(sx + 8, sy + 8)
            if state == "waiting":
                p.setBrush(Qt.BrushStyle.NoBrush)
                p.setPen(QPen(muted, 1.5))
                p.drawEllipse(c, 5, 5)
            elif state == "active":
                halo = QColor(GREEN)
                halo.setAlphaF(0.25 * blink)
                p.setPen(Qt.PenStyle.NoPen)
                p.setBrush(halo)
                p.drawEllipse(c, 9, 9)
                dot = QColor(GREEN)
                dot.setAlphaF(blink)
                p.setBrush(dot)
                p.drawEllipse(c, 5, 5)
            else:
                p.setPen(Qt.PenStyle.NoPen)
                p.setBrush(GREEN)
                p.drawEllipse(c, 8, 8)
                p.setPen(QPen(WHITE, 2, Qt.PenStyle.SolidLine, Qt.PenCapStyle.RoundCap, Qt.PenJoinStyle.RoundJoin))
                p.drawPolyline([QPointF(c.x() - 3.5, c.y()), QPointF(c.x() - 1, c.y() + 3), QPointF(c.x() + 4, c.y() - 3)])
            p.setPen(muted if state == "waiting" else text_color)
            p.drawText(QRectF(sx + 24, sy - 2, 300, 20), Qt.AlignmentFlag.AlignVCenter | Qt.AlignmentFlag.AlignLeft, label)
            sy += 24

        p.setPen(muted)
        f.setPointSize(8)
        p.setFont(f)
        p.drawText(QRectF(0, sy + 4, w, 16), Qt.AlignmentFlag.AlignCenter, "Keep this window open - the receipt fills in by itself.")

    def _paint_success(self, p: QPainter, text_color: QColor):
        w = self.width()
        t = time.monotonic() - self._success_t0
        bloom = min(1.0, t / 1.1)
        scale = 0.4 + 0.6 * _ease_out_back(min(1.0, t / 0.5))
        check = max(0.0, min(1.0, (t - 0.25) / 0.45))
        c = QPointF(w / 2, self.height() / 2)
        r = 40 * scale

        ring = QColor(GREEN)
        ring.setAlphaF(max(0.0, (1 - bloom) * 0.5))
        p.setBrush(Qt.BrushStyle.NoBrush)
        p.setPen(QPen(ring, 3))
        p.drawEllipse(c, r + 46 * bloom, r + 46 * bloom)

        for i in range(10):
            a = math.radians(i * 36 + 12)
            dist = 44 + 46 * bloom
            color = QColor(LEAF)
            color.setAlphaF(max(0.0, min(1.0, 1 - bloom * 0.8)))
            self._draw_leaf(p, QPointF(c.x() + math.cos(a) * dist, c.y() + math.sin(a) * dist), 14, i * 36 + 12, color)

        p.setPen(Qt.PenStyle.NoPen)
        p.setBrush(GREEN)
        p.drawEllipse(c, r, r)

        # The check draws itself along its two strokes.
        pts = [QPointF(c.x() - r * 0.42, c.y() + r * 0.02), QPointF(c.x() - r * 0.1, c.y() + r * 0.34), QPointF(c.x() + r * 0.46, c.y() - r * 0.32)]
        seg = [math.dist((a.x(), a.y()), (b.x(), b.y())) for a, b in zip(pts, pts[1:])]
        remaining = check * sum(seg)
        line = [pts[0]]
        for (a, b), length in zip(zip(pts, pts[1:]), seg):
            if remaining >= length:
                line.append(b)
                remaining -= length
            else:
                k = remaining / length if length else 0
                line.append(QPointF(a.x() + (b.x() - a.x()) * k, a.y() + (b.y() - a.y()) * k))
                break
        p.setPen(QPen(WHITE, 5, Qt.PenStyle.SolidLine, Qt.PenCapStyle.RoundCap, Qt.PenJoinStyle.RoundJoin))
        if len(line) > 1:
            p.drawPolyline(line)

    # ---- shapes --------------------------------------------------------

    @staticmethod
    def _draw_leaf(p: QPainter, center: QPointF, length: float, angle: float, color: QColor):
        p.save()
        p.translate(center)
        p.rotate(angle)
        p.setPen(Qt.PenStyle.NoPen)
        p.setBrush(color)
        p.drawPath(_leaf_path(length))
        spine = QColor(255, 255, 255, 90)
        p.setPen(QPen(spine, max(1.0, length * 0.06)))
        p.drawLine(QPointF(-length / 2, 0), QPointF(length / 2, 0))
        p.restore()

    @staticmethod
    def _draw_tree(p: QPainter, c: QPointF, h: float):
        w = h * 0.7
        p.setPen(Qt.PenStyle.NoPen)
        p.setBrush(WHITE)
        top = QPainterPath()
        top.moveTo(c.x(), c.y() - h / 2)
        top.lineTo(c.x() + w / 2, c.y() + h * 0.05)
        top.lineTo(c.x() - w / 2, c.y() + h * 0.05)
        top.closeSubpath()
        bottom = QPainterPath()
        bottom.moveTo(c.x(), c.y() - h * 0.22)
        bottom.lineTo(c.x() + w * 0.62, c.y() + h * 0.32)
        bottom.lineTo(c.x() - w * 0.62, c.y() + h * 0.32)
        bottom.closeSubpath()
        p.drawPath(top)
        p.drawPath(bottom)
        p.drawRect(QRectF(c.x() - h * 0.05, c.y() + h * 0.32, h * 0.1, h * 0.16))

    @staticmethod
    def _draw_person(p: QPainter, c: QPointF, r: float):
        p.setPen(Qt.PenStyle.NoPen)
        p.setBrush(WHITE)
        p.drawEllipse(QPointF(c.x(), c.y() - r * 0.28), r * 0.32, r * 0.32)
        p.drawPie(QRectF(c.x() - r * 0.55, c.y() + r * 0.08, r * 1.1, r * 0.9), 0, 180 * 16)
