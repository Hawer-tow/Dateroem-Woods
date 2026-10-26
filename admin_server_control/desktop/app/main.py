import sys
from pathlib import Path

from PySide6.QtCore import QEasingCurve, QParallelAnimationGroup, QPropertyAnimation
from PySide6.QtGui import QIcon
from PySide6.QtWidgets import QApplication

from app.core import http
from app.core.api_client import ApiClient
from app.core.foreground import bring_to_front
from app.core.session import Session
from app.core.single_instance import SingleInstanceGuard
from app.core.windows_app_id import set_windows_app_id
from app.ui.login_window import LoginWindow
from app.ui.main_window import MainWindow
from app.ui import theme
from app.ui.splash_screen import SplashScreen
from app.ui.theme import STYLESHEET

ICON_PATH = Path(__file__).resolve().parent / "assets" / "icon.ico"


def _server_manager():
    # "share it as an application to other admins" - the copy given to
    # other admins leaves out core/server_manager.py (it starts and stops
    # this computer's own servers, and holds this computer's tunnel
    # settings), so there is simply no Servers panel in it.
    try:
        from app.core.server_manager import ServerManager
    except ImportError:
        return None
    return ServerManager()


def main(app=None, guard=None, boot=None):
    # launch.pyw passes in an already-created QApplication, single-instance
    # guard and "Starting..." window so something is on screen before the
    # imports above finish; running this module directly builds its own.
    if app is None:
        set_windows_app_id()
        app = QApplication(sys.argv)

    # Only one copy of this app should ever be running - a second launch
    # (double-clicking the taskbar/Start Menu icon again) just raises the
    # existing window and exits immediately, the way WhatsApp/Netflix
    # behave on desktop, instead of piling up duplicate instances.
    if guard is None:
        guard = SingleInstanceGuard()
        if not guard.try_acquire():
            return

    app.setApplicationName("Dateroem Admin Control")
    app.setApplicationDisplayName("Dateroem Admin Control")
    app.setStyleSheet(STYLESHEET)
    app_icon = QIcon(str(ICON_PATH))
    app.setWindowIcon(app_icon)

    session = Session()
    api_client = ApiClient(session)
    # One ServerManager for the whole app lifetime, shared by the
    # pre-login "Manage Servers" window and the post-login Servers
    # section, so both ever see - and control - the exact same processes.
    # None in the copy shared with other admins (see _server_manager).
    server_manager = _server_manager()
    state = {"main_window": None, "transition_anim": None}

    def _crossfade(hide_widget, show_widget, on_finished=None, duration=420):
        # "not just rapid appearance ... like an effect but still
        # following the forest like theme" - both screens already share
        # the same dark forest palette (theme.py), so a plain crossfade
        # reads as one continuous scene dissolving into the next rather
        # than a jump cut, without needing any extra forest-specific
        # decoration on top of it.
        #
        # This animates each top-level window's own windowOpacity (the
        # native OS-level window alpha) rather than a QGraphicsOpacityEffect
        # on the widget tree - an effect sitting on top of a QMainWindow
        # full of its own live sections (each with in-flight background
        # Workers updating table/label contents) turned out to corrupt
        # those widgets' paint/event handling badly enough to crash the
        # whole app a few seconds in ("Signal source has been deleted"
        # from worker.py, then the process gone). windowOpacity never
        # touches the widget tree at all, so it can't interact with that.
        hide_widget.setEnabled(False)
        show_widget.setWindowOpacity(0.0)

        fade_in = QPropertyAnimation(show_widget, b"windowOpacity", show_widget)
        fade_in.setDuration(duration)
        fade_in.setStartValue(0.0)
        fade_in.setEndValue(1.0)
        fade_in.setEasingCurve(QEasingCurve.Type.OutCubic)

        fade_out = QPropertyAnimation(hide_widget, b"windowOpacity", hide_widget)
        fade_out.setDuration(duration)
        fade_out.setStartValue(1.0)
        fade_out.setEndValue(0.0)
        fade_out.setEasingCurve(QEasingCurve.Type.OutCubic)

        group = QParallelAnimationGroup()
        group.addAnimation(fade_in)
        group.addAnimation(fade_out)

        def _on_finished():
            # Reset so a window that gets shown again later (login window
            # after a future logout) isn't still stuck at opacity 0.
            hide_widget.setWindowOpacity(1.0)
            hide_widget.setEnabled(True)
            state["transition_anim"] = None
            if on_finished:
                on_finished()

        group.finished.connect(_on_finished)
        # QParallelAnimationGroup has no other owner keeping it alive -
        # without this reference Python garbage-collects it mid-flight and
        # the animation silently never completes (same "Worker" lifetime
        # issue documented on RecordFormDialog/TripDetailDialog).
        state["transition_anim"] = group
        group.start()

    def show_main_window():
        # An admin reset this account's password (migration 039): a new one
        # of the person's own comes first - until then the account can do
        # nothing else, so the main window would only show refusals.
        if session.must_change_password:
            from app.ui.password_change_dialog import PasswordChangeDialog

            if not PasswordChangeDialog(api_client, session, forced=True, parent=login_window).exec():
                session.clear()
                login_window.password_input.clear()
                login_window.error_label.setText("Signed out. Choose your new password the next time you sign in.")
                return
        # "even if i log in its still remains there bearing in mind i have
        # already the server section inside when i log in" - the pre-login
        # Manage Servers window is redundant the moment login succeeds
        # (MainWindow's own Servers sidebar tab shares the same
        # ServerManager), so close it here rather than leaving it as a
        # second, easy-to-forget top-level window.
        login_window.close_server_window()
        # "the page disappears before the main page comes in - that
        # breakdown" - building the new window and starting the crossfade
        # before the old one is ever hidden means something is always on
        # screen - no gap, no jump cut, just the animated dissolve.
        # The account's colour theme (Forest, Dark or Light), in place before
        # the main window is built so it is drawn in it from the start.
        theme.set_theme(session.theme)
        main_window = MainWindow(
            session, api_client, server_manager, on_logout=show_login_window, on_theme_changed=restyle_main_window
        )
        main_window.setWindowIcon(app_icon)
        state["main_window"] = main_window
        main_window.showMaximized()
        _crossfade(login_window, main_window, on_finished=login_window.hide)

    def restyle_main_window(section: int):
        """Settings changed the colour theme (theme.set_theme has run): the
        same signed-in main window is built again in the new colours and
        faded in over the old one, on the same section - no signing in again.
        Building it afresh redraws every icon and inline style at once."""
        old_main = state["main_window"]
        if old_main is None or state["transition_anim"] is not None:
            return
        new_main = MainWindow(
            session, api_client, server_manager, on_logout=show_login_window,
            on_theme_changed=restyle_main_window, start_section=section,
        )
        new_main.setWindowIcon(app_icon)
        state["main_window"] = new_main
        new_main.showMaximized()
        _crossfade(old_main, new_main, on_finished=old_main.close, duration=320)

    def show_login_window(message: str = ""):
        # Sign-in always shows the forest, like its photograph.
        theme.set_theme(theme.DEFAULT_THEME)
        session.clear()
        login_window.username_input.clear()
        login_window.password_input.clear()
        login_window.error_label.setText(message)
        old_main = state["main_window"]
        login_window.showMaximized()
        if old_main is not None:
            def _close_old_main():
                old_main.close()
                if state["main_window"] is old_main:
                    state["main_window"] = None

            _crossfade(old_main, login_window, on_finished=_close_old_main)

    login_window = LoginWindow(api_client, server_manager, on_success=show_main_window)
    login_window.setWindowIcon(app_icon)

    def end_session(message: str):
        # The sign-in lasts a working day, and blocking this computer or the
        # account ends it at once - either way every open screen's next call
        # is refused, so go back to signing in (once, however many calls
        # were refused together) and say why.
        if state["main_window"] is not None and state["transition_anim"] is None:
            show_login_window(message or "Please sign in again.")

    api_client.signals.session_ended.connect(end_session)

    def activate_existing_window():
        win = state["main_window"] or login_window
        win.showMaximized()
        win.raise_()
        win.activateWindow()

    guard.activate_requested.connect(activate_existing_window)

    # The intro plays once, on cold start - the phone app's entrance (the
    # misty forest rising out of the mist) - and dissolves into the sign-in
    # screen, which carries on the same forest. Logging out later just
    # returns straight to the sign-in screen, no replay (this is an admin
    # tool people open and close often; re-showing a multi-second animation
    # every time would get old fast).
    #
    # "why play it then remove it should be one" (26 October 2026): the
    # sign-in window opens first, taking over from the Starting window while
    # this app is still the one in front, and the intro plays as a layer
    # inside it. It used to be a separate full-screen window; when it closed,
    # Windows would not bring the sign-in window forward (it only flashed
    # its taskbar button), so one had to click it.
    def intro_ending():
        login_window.end_intro()
        # The network library loads on first use (core/http.py); load it
        # now, while the person is still typing, so signing in doesn't wait.
        http.warm_up()

    def focus_login():
        # Once the intro has dissolved, the cursor waits in Username.
        bring_to_front(login_window)
        login_window.username_input.setFocus()

    login_window.begin_intro()
    login_window.showMaximized()
    splash = SplashScreen(login_window, on_finish=intro_ending, on_closed=focus_login)
    splash.show()
    splash.raise_()
    bring_to_front(login_window)
    splash.setFocus()
    if boot is not None:
        boot.close()

    sys.exit(app.exec())


if __name__ == "__main__":
    main()
