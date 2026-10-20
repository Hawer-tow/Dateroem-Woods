import sys

APP_USER_MODEL_ID = "Dateroem.AdminControl.Desktop.1"


def set_windows_app_id():
    # Without this, Windows groups the taskbar entry and Start Menu
    # search result under generic "Python", using python.exe's own icon
    # instead of ours - this tells Explorer "this is its own distinct
    # app," independent of which interpreter happens to be running it.
    # Must run before QApplication() exists; a no-op on non-Windows.
    # Kept in its own tiny module so the launcher can call it without
    # importing the whole app first.
    if sys.platform != "win32":
        return
    try:
        import ctypes

        ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID(APP_USER_MODEL_ID)
    except Exception:
        pass
