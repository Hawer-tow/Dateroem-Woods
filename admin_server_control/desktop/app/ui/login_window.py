import math
import platform
import random
import time

from PySide6.QtCore import QAbstractAnimation, QEasingCurve, QPoint, QRect, QRectF, QThreadPool, Qt, QTimer, QVariantAnimation
from PySide6.QtGui import QBrush, QColor, QPainter, QPen, QPixmap
from PySide6.QtWidgets import (
    QFrame,
    QGraphicsOpacityEffect,
    QHBoxLayout,
    QInputDialog,
    QLabel,
    QLineEdit,
    QMessageBox,
    QPushButton,
    QVBoxLayout,
    QWidget,
)

from app.core import server_address
from app.core.api_client import readable
from app.core.worker import Worker
from app.ui import theme
from app.ui.entrance_paint import MIST_AT_END_MS, RESTING_MIST_BANDS, EntrancePictures, draw_forest_backdrop
from app.ui.forest_paint import ease as _ease
from app.ui.icons import icon

CARD_WIDTH = 420


def _smooth(x: float) -> float:
    x = min(1.0, max(0.0, x))
    return x * x * (3 - 2 * x)


class _Scrim(QWidget):
    """Gently dims the forest while the servers card is up; a click beside the card goes back to sign-in."""

    def __init__(self, on_click, parent):
        super().__init__(parent)
        self._on_click = on_click
        self.level = 0.0

    def paintEvent(self, _event):
        # The forest's own deep shade, not plain black.
        QPainter(self).fillRect(self.rect(), QColor(5, 11, 7, int(160 * self.level)))

    def mousePressEvent(self, _event):
        self._on_click()


class _ServerCard(QFrame):
    """The servers, as a card in the middle of the sign-in screen, in place of
    the sign-in card (see _MistSwap for how the two trade places).

    "when one minimizes the server it goes down at the left side ... for a
    person it would be difficult to spot where it went" (19 October 2026):
    this used to be a separate window belonging to the sign-in window, which
    Windows minimizes into a small bar in the bottom-left corner of the
    screen. Then it slid in from the right over the sign-in card; since 20
    October 2026 the two cards never share the screen ("so that the two dont
    feel like overlapping features"). Back to sign in, Esc or a click beside
    the card returns, and the servers keep running either way."""

    def __init__(self, manager, on_back, parent):
        from app.ui.widgets.server_control_panel import ServerControlPanel  # only in the owner's copy

        super().__init__(parent)
        self.setObjectName("serverCard")
        # The misty forest's colours rather than the app's stronger green
        # ("it feels too green ... can it also be made to feel like part of the
        # colors there", 20 October 2026): a soft grey-green taken from the
        # mist and trees around it, slightly see-through, with the app's wood
        # accents (buttons, the selected tab's edge) still pointing to the
        # theme inside.
        self.setStyleSheet(
            f"""
            #serverCard {{ background-color: rgba(28, 41, 35, 0.94); border: 1px solid rgba(255, 255, 255, 0.10);
                          border-top: 3px solid {theme.WOOD_SWEEP}; border-radius: 14px; }}
            #serverCard QTabBar::tab {{ background-color: rgba(255, 255, 255, 0.04); border-color: rgba(255, 255, 255, 0.08); }}
            #serverCard QTabBar::tab:selected {{ background-color: rgba(12, 22, 18, 0.72); }}
            #serverCard QTabWidget::pane {{ border: 1px solid rgba(255, 255, 255, 0.08); }}
            #serverCard QPlainTextEdit {{ background-color: rgba(12, 22, 18, 0.72); border: 1px solid rgba(255, 255, 255, 0.08); }}
            """
        )
        layout = QVBoxLayout(self)
        layout.setContentsMargins(10, 12, 10, 8)
        layout.setSpacing(0)
        top = QHBoxLayout()
        back_btn = QPushButton("Back to sign in")
        back_btn.setIcon(icon("arrow-left"))
        back_btn.setToolTip("Back to the sign-in card (Esc, or click beside this card). The servers keep running.")
        back_btn.clicked.connect(on_back)
        top.addWidget(back_btn)
        top.addStretch(1)
        layout.addLayout(top)
        layout.addWidget(ServerControlPanel(manager), 1)


class _MistSwap(QWidget):
    """The two cards trading places through the mist (20 October 2026: the
    sign-in card should "move towards the back shrinking and in the shrinking
    process turn into a mist which will go to the back image as the mist and
    another mist will approach and as it approaches it will produce the
    manage server card").

    Drawn from pictures of the two cards, over the live forest: the card
    going away shrinks back and fades while a cloud of mist spreads from it
    and thins into the forest; a second cloud comes forward, and the other
    card takes shape inside it as it thins. Clicks pass through."""

    def __init__(self, pictures, parent):
        super().__init__(parent)
        self.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents)
        self.pictures = pictures
        self.leaving = None   # (picture, rect) of the card going back into the mist
        self.coming = None    # (picture, rect) of the card coming out of it
        self.t = 0.0

    @staticmethod
    def _card(p: QPainter, pix: QPixmap, rect: QRectF, scale: float, opacity: float, lift: float):
        if opacity <= 0:
            return
        w, h = rect.width() * scale, rect.height() * scale
        p.setOpacity(min(opacity, 1.0))
        p.drawPixmap(QRectF(rect.center().x() - w / 2, rect.center().y() - h / 2 - lift, w, h), pix, QRectF(pix.rect()))
        p.setOpacity(1.0)

    def _puff(self, p: QPainter, rect: QRectF, size: float, opacity: float):
        if opacity <= 0:
            return
        puff = self.pictures.mist_puff(rect.width() * 1.5, rect.height() * 1.15)
        w, h = rect.width() * 1.5 * size, rect.height() * 1.15 * size
        p.setOpacity(min(opacity, 1.0))
        p.drawPixmap(QRectF(rect.center().x() - w / 2, rect.center().y() - h / 2, w, h), puff, QRectF(puff.rect()))
        p.setOpacity(1.0)

    def paintEvent(self, _event):
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform)
        t = self.t
        if self.leaving is not None:
            pix, rect = self.leaving
            a = _smooth(t / 0.55)
            # Shrinking back, fading, a little higher - into the forest ...
            self._card(p, pix, rect, 1 - 0.24 * a, 1 - a, rect.height() * 0.05 * a)
            # ... turning into mist that spreads and thins away.
            self._puff(p, rect, 0.85 + 0.55 * a, 0.85 * math.sin(math.pi * min(1.0, t / 0.62)))
        if self.coming is not None:
            pix, rect = self.coming
            b = min(1.0, max(0.0, (t - 0.32) / 0.68))
            e = _smooth(b)
            # The card taking shape inside the approaching mist ...
            self._card(p, pix, rect, 0.84 + 0.16 * e, _smooth((b - 0.2) / 0.8), -rect.height() * 0.03 * (1 - e))
            # ... as the mist comes forward and thins.
            self._puff(p, rect, 0.55 + 0.6 * e, 0.9 * math.sin(math.pi * b))


class _Emblem(QLabel):
    """The company emblem above the sign-in form - "the new one will take
    over" from the painted pine that stood here (18 October 2026)."""

    SIZE = 72

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setFixedSize(self.SIZE, self.SIZE)
        self.setPixmap(theme.emblem_pixmap(self.SIZE, self.devicePixelRatioF(), bold=True))


class _Spinner(QWidget):
    """A small rotating ring - "when i click sign in i have no ui immediate
    response ... is it possible for a rotating feature to allow one to see
    that authentication is actually occurring". Only runs while visible,
    so it costs nothing the rest of the time."""

    def __init__(self, parent=None, diameter: int = 20):
        super().__init__(parent)
        self.setFixedSize(diameter, diameter)
        self._angle = 0
        self._timer = QTimer(self)
        self._timer.timeout.connect(self._tick)
        self.hide()

    def start(self):
        self._angle = 0
        self.show()
        self._timer.start(16)

    def stop(self):
        self._timer.stop()
        self.hide()

    def _tick(self):
        self._angle = (self._angle + 9) % 360
        self.update()

    def paintEvent(self, _event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        pen = QPen(QColor(theme.ACCENT))
        pen.setWidth(3)
        pen.setCapStyle(Qt.PenCapStyle.RoundCap)
        painter.setPen(pen)
        rect = self.rect().adjusted(2, 2, -2, -2)
        painter.drawArc(rect, -self._angle * 16, 100 * 16)


class _Speck:
    """A drifting mote of sawdust - the "lively" ambient touch behind the
    login card, in place of a plain flat background."""

    __slots__ = ("x", "y", "speed", "drift", "radius", "alpha")

    def __init__(self):
        self.x = random.random()
        self.y = random.random()
        self.speed = random.uniform(0.006, 0.018)
        self.drift = random.uniform(-0.15, 0.15)
        self.radius = random.uniform(1.0, 2.6)
        self.alpha = random.uniform(0.15, 0.45)


class LoginWindow(QWidget):
    def __init__(self, api_client, server_manager, on_success, parent=None):
        super().__init__(parent)
        self.api_client = api_client
        self.server_manager = server_manager
        self.on_success = on_success
        # The servers card, the dimming behind it and the mist the two cards
        # trade places through - made on first use of Manage Servers.
        self._server_card = None
        self._scrim = None
        self._swap_layer = None
        self._swap_anim = None
        self._servers_shown = False

        self.setWindowTitle("Sign In")

        self._specks = [_Speck() for _ in range(45)]
        self._anim_start = time.monotonic()
        # The intro's forest, mist and green wash behind the card (see paintEvent).
        self._pics = EntrancePictures(self)
        self._shown_at = time.monotonic()
        self._anim_timer = QTimer(self)
        self._anim_timer.timeout.connect(self._tick)

        # Window opens maximized (main.py calls showMaximized()), so the
        # form itself is a fixed-width card centered in the middle via
        # stretch on all four sides, rather than a QVBoxLayout stretching
        # its fields across the whole screen.
        outer = QVBoxLayout(self)
        outer.setContentsMargins(0, 0, 0, 0)
        outer.addStretch(1)

        row = QHBoxLayout()
        row.addStretch(1)

        card = QFrame()
        card.setProperty("role", "card")
        # A touch of the forest shows through the card.
        card.setObjectName("signInCard")
        card.setStyleSheet("#signInCard { background-color: rgba(23, 54, 40, 0.90); }")
        card.setFixedWidth(CARD_WIDTH)
        self._card = card
        layout = QVBoxLayout(card)
        layout.setContentsMargins(44, 40, 44, 40)
        layout.setAlignment(Qt.AlignmentFlag.AlignTop)

        icon_row = QHBoxLayout()
        icon_row.addStretch(1)
        icon_row.addWidget(_Emblem())
        icon_row.addStretch(1)
        layout.addLayout(icon_row)
        layout.addSpacing(12)

        # The tree-ring wordmark from the website's navbar, with the tool's
        # own name under it.
        wordmark = QLabel()
        wordmark.setPixmap(theme.wordmark_pixmap(300, self.devicePixelRatioF()))
        wordmark.setAlignment(Qt.AlignmentFlag.AlignHCenter)
        layout.addWidget(wordmark)
        layout.addSpacing(6)
        title = QLabel("Admin Control")
        title.setProperty("role", "title")
        title.setAlignment(Qt.AlignmentFlag.AlignHCenter)
        layout.addWidget(title)
        subtitle = QLabel("Sign in with an Admin or SuperAdmin account.")
        subtitle.setProperty("role", "muted")
        subtitle.setWordWrap(True)
        subtitle.setAlignment(Qt.AlignmentFlag.AlignHCenter)
        layout.addWidget(subtitle)

        layout.addSpacing(28)

        username_label = QLabel("Username")
        username_label.setProperty("role", "muted")
        layout.addWidget(username_label)
        layout.addSpacing(4)
        self.username_input = QLineEdit()
        self.username_input.setProperty("role", "login-field")
        self.username_input.setPlaceholderText("Enter your username")
        layout.addWidget(self.username_input)

        layout.addSpacing(18)

        password_label = QLabel("Password")
        password_label.setProperty("role", "muted")
        layout.addWidget(password_label)
        layout.addSpacing(4)
        self.password_input = QLineEdit()
        self.password_input.setProperty("role", "login-field")
        self.password_input.setPlaceholderText("Enter your password")
        self.password_input.setEchoMode(QLineEdit.EchoMode.Password)
        self.password_input.returnPressed.connect(self._attempt_login)
        layout.addWidget(self.password_input)

        self.error_label = QLabel("")
        self.error_label.setProperty("role", "error")
        self.error_label.setWordWrap(True)
        layout.addSpacing(10)
        layout.addWidget(self.error_label)

        layout.addSpacing(20)

        button_row = QHBoxLayout()
        button_row.setSpacing(12)
        self.login_btn = QPushButton("Sign In")
        self.login_btn.setProperty("role", "primary")
        self.login_btn.setMinimumHeight(42)
        self.login_btn.clicked.connect(self._attempt_login)
        button_row.addWidget(self.login_btn, 1)
        self.spinner = _Spinner()
        button_row.addWidget(self.spinner)
        layout.addLayout(button_row)

        layout.addSpacing(14)
        # "im puzzled on how this will work since logging into the desktop
        # i think requires it to have the backend server on" - this is the
        # way out of that: pure local process control, no login needed.
        # Only when the servers are on this computer, and only in the
        # owner's copy of the app (see main.py's _server_manager).
        self.servers_btn = QPushButton("Manage Servers")
        self.servers_btn.setIcon(icon("server"))
        self.servers_btn.setToolTip("Start the servers on this computer (the servers card takes this card's place)")
        self.servers_btn.clicked.connect(self._open_server_control)
        layout.addWidget(self.servers_btn)

        # "in the day i move to the provider its a matter of changing the
        # addresses port etc" - which server this app signs in to, and the
        # way to point it somewhere else.
        layout.addSpacing(10)
        server_row = QHBoxLayout()
        self.server_label = QLabel()
        self.server_label.setProperty("role", "muted")
        self.server_label.setWordWrap(True)
        server_row.addWidget(self.server_label, 1)
        change_server_btn = QPushButton("Change")
        change_server_btn.clicked.connect(self._change_server)
        server_row.addWidget(change_server_btn)
        layout.addLayout(server_row)
        self._show_server()

        row.addWidget(card)
        row.addStretch(1)
        outer.addLayout(row)
        outer.addStretch(1)

        # Keeps the in-flight Worker (and its QObject-based signals) alive
        # for as long as this window exists - without a real reference
        # somewhere, Python garbage-collects the Worker the instant
        # _attempt_login() returns (QThreadPool.start() doesn't itself
        # keep the Python wrapper alive), silently dropping its signals
        # before they ever fire. That's why the button looked "dead":
        # the login request really was sent, its result just never made
        # it back to the UI.
        self._active_workers = []

    def _attempt_login(self):
        username = self.username_input.text().strip()
        password = self.password_input.text()
        if not username or not password:
            self.error_label.setText("Enter a username and password.")
            return

        self.login_btn.setEnabled(False)
        self.login_btn.setText("Signing in...")
        self.spinner.start()
        self.error_label.setText("")

        def work():
            return self.api_client.login(username, password)

        def on_done(data):
            self._active_workers.remove(worker)
            self.spinner.stop()
            self.login_btn.setEnabled(True)
            self.login_btn.setText("Sign In")
            if self.api_client.session.is_admin:
                self.on_success()
            else:
                self.api_client.session.clear()
                self.error_label.setText(
                    f"Signed in, but the '{data.get('roleName')}' role doesn't have access to this tool "
                    "- Admin or SuperAdmin only."
                )

        def on_error(message):
            self._active_workers.remove(worker)
            self.spinner.stop()
            self.login_btn.setEnabled(True)
            self.login_btn.setText("Sign In")
            text = readable(message)
            if "COMPUTER_PENDING" in message:
                # So the SuperAdmin knows which line to approve.
                text += f"\n\nThis computer is listed as \"{platform.node()}\"."
            self.error_label.setText(text)

        worker = Worker(work)
        worker.signals.finished.connect(on_done)
        worker.signals.error.connect(on_error)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)

    def _show_server(self):
        address = server_address.current()
        self.server_label.setText(f"Server: {address}")
        self.servers_btn.setVisible(self.server_manager is not None and server_address.is_this_computer())

    def _change_server(self):
        hint = "The address of the Dateroem admin server, for example https://admin.example.com"
        if self.server_manager is not None:
            hint += f"\n(this computer's own server is {server_address.DEFAULT})."
        address, ok = QInputDialog.getText(self, "Server address", hint, text=server_address.current())
        if not ok or not address.strip():
            return
        problem = server_address.problem(address)
        if problem:
            QMessageBox.warning(self, "Can't use that address", problem)
            return
        server_address.save(address)
        self._show_server()
        self.error_label.setText("")

    # ---- Manage Servers: the two cards trading places through the mist ----------

    SWAP_MS = 1100

    def _open_server_control(self):
        self._show_servers()

    def _swapping(self) -> bool:
        return self._swap_anim is not None and self._swap_anim.state() == QAbstractAnimation.State.Running

    def _server_card_rect(self) -> QRect:
        """The servers card in the middle of the screen, where the sign-in card
        stood - wider, so the server tabs and logs fit comfortably."""
        w = min(1060, max(640, int(self.width() * 0.62)), self.width() - 48)
        h = min(780, max(480, int(self.height() * 0.84)), self.height() - 48)
        return QRect((self.width() - w) // 2, (self.height() - h) // 2, w, h)

    @staticmethod
    def _picture(widget: QWidget) -> tuple[QPixmap, QRectF]:
        """A picture of a card as it looks right now, with where it stands.
        A hidden card is shown just long enough to be laid out (nothing is
        painted in between)."""
        hidden = widget.isHidden()
        if hidden:
            widget.show()
        widget.ensurePolished()
        if widget.layout() is not None:
            widget.layout().activate()
        pix = widget.grab()
        if hidden:
            widget.hide()
        return pix, QRectF(widget.geometry())

    def _hide_sign_in_card(self, hidden: bool):
        """The sign-in card stays in its place (so nothing moves) but can't be seen while the servers card is up."""
        if hidden:
            effect = QGraphicsOpacityEffect(self._card)
            effect.setOpacity(0.0)
            self._card.setGraphicsEffect(effect)
        else:
            self._card.setGraphicsEffect(None)

    def _swap(self, leaving, coming, opening: bool, done):
        layer = self._swap_layer
        layer.leaving, layer.coming, layer.t = leaving, coming, 0.0
        layer.setGeometry(self.rect())
        layer.show()
        layer.raise_()
        anim = QVariantAnimation(self)
        anim.setStartValue(0.0)
        anim.setEndValue(1.0)
        anim.setDuration(self.SWAP_MS)

        def step(value):
            layer.t = float(value)
            layer.update()
            self._scrim.level = 0.45 * (layer.t if opening else 1 - layer.t)
            self._scrim.update()

        anim.valueChanged.connect(step)
        anim.finished.connect(done)
        # Kept referenced, or Python collects it mid-way.
        self._swap_anim = anim
        anim.start()

    def _ensure_server_card(self):
        """Makes the servers card (hidden) - as soon as sign-in opens on the
        owner's computer, so it has heard every server's status by the time
        Manage Servers is pressed and its picture shows them right."""
        if self._server_card is None and self.server_manager is not None:
            self._scrim = _Scrim(self._hide_servers, self)
            self._scrim.hide()
            self._swap_layer = _MistSwap(self._pics, self)
            self._swap_layer.hide()
            self._server_card = _ServerCard(self.server_manager, self._hide_servers, self)
            self._server_card.hide()

    def _show_servers(self):
        if self._servers_shown or self._swapping():
            return
        self._ensure_server_card()
        self._server_card.setGeometry(self._server_card_rect())
        coming = self._picture(self._server_card)
        leaving = self._picture(self._card)
        self._hide_sign_in_card(True)
        self._scrim.setGeometry(self.rect())
        self._scrim.level = 0.0
        self._scrim.show()
        self._scrim.raise_()
        self._servers_shown = True

        def done():
            self._swap_layer.hide()
            self._server_card.show()
            self._server_card.raise_()
            self._server_card.setFocus()

        self._swap(leaving, coming, True, done)

    def _hide_servers(self):
        if not self._servers_shown or self._swapping():
            return
        leaving = self._picture(self._server_card)
        self._server_card.hide()
        self._hide_sign_in_card(False)
        coming = self._picture(self._card)
        self._hide_sign_in_card(True)
        self._servers_shown = False

        def done():
            self._swap_layer.hide()
            self._scrim.hide()
            self._hide_sign_in_card(False)
            self.username_input.setFocus()

        self._swap(leaving, coming, False, done)

    def close_server_window(self):
        """Removes the servers card at once. "even if i log in its still
        remains there" - after signing in, the main window's own Servers
        section (same shared ServerManager) takes over, so main.py calls
        this; closing this window does too."""
        if self._swap_anim is not None:
            self._swap_anim.stop()
            self._swap_anim = None
        for widget in (self._server_card, self._scrim, self._swap_layer):
            if widget is not None:
                widget.deleteLater()
        self._server_card = self._scrim = self._swap_layer = None
        self._servers_shown = False
        self._hide_sign_in_card(False)

    def resizeEvent(self, event):
        super().resizeEvent(event)
        if self._server_card is not None and self._server_card.isVisible():
            self._server_card.setGeometry(self._server_card_rect())
        for widget in (self._scrim, self._swap_layer):
            if widget is not None and widget.isVisible():
                widget.setGeometry(self.rect())

    def keyPressEvent(self, event):
        if event.key() == Qt.Key.Key_Escape and self._servers_shown:
            self._hide_servers()
            return
        super().keyPressEvent(event)

    def closeEvent(self, event):
        # "when shutting the app i have to close the two" - closing this
        # window (whether that's the whole app quitting, or just this
        # screen) should take the pre-login server window with it instead
        # of leaving it as its own independent, easy-to-forget window.
        self.close_server_window()
        super().closeEvent(event)

    def showEvent(self, event):
        super().showEvent(event)
        self._shown_at = time.monotonic()
        self._anim_timer.start(40)
        if self.servers_btn.isVisibleTo(self):
            QTimer.singleShot(0, self._ensure_server_card)

    def hideEvent(self, event):
        super().hideEvent(event)
        self._anim_timer.stop()

    def _tick(self):
        self.update()

    def _screen_frame(self) -> QRectF:
        """The whole screen, in this window's coordinates - the intro filled
        the screen, so drawing its picture here at the screen's place lines
        sign-in's backdrop up exactly with where the intro left it."""
        screen = self.screen().geometry() if self.screen() is not None else self.rect()
        origin = self.mapToGlobal(QPoint(0, 0))
        return QRectF(screen.x() - origin.x(), screen.y() - origin.y(), screen.width(), screen.height())

    def paintEvent(self, _event):
        # The intro's last picture without the brand - the misty pine
        # forest, its drifting mist and the app's green wash (19 October
        # 2026: "let it feel like a transition graceful") - with slow
        # sawdust specks for a bit of life. Painted directly on this widget
        # so the card (a separate QFrame child) simply sits on top of it.
        painter = QPainter(self)
        w, h = self.width(), self.height()
        if w <= 0 or h <= 0:
            return
        frame = self._screen_frame()
        if not self._pics.fog.isNull():
            self._pics.prepare_fog(frame.height(), RESTING_MIST_BANDS)
        mist_t = MIST_AT_END_MS + (time.monotonic() - self._shown_at) * 1000
        draw_forest_backdrop(self._pics, painter, mist_t, frame, QRectF(self.rect()))

        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        t = time.monotonic() - self._anim_start
        for speck in self._specks:
            phase = (speck.y - speck.speed * t) % 1.0
            x = (speck.x + speck.drift * t * 0.05) % 1.0
            wobble = 0.01 * _ease(QEasingCurve.Type.InOutSine, (t * 0.3 + speck.x) % 1.0)
            color = QColor(0xE0, 0xC9, 0x9A)
            color.setAlphaF(speck.alpha)
            painter.setPen(Qt.PenStyle.NoPen)
            painter.setBrush(QBrush(color))
            px = (x + wobble) * w
            py = phase * h
            painter.drawEllipse(int(px), int(py), max(1, int(speck.radius)), max(1, int(speck.radius)))
