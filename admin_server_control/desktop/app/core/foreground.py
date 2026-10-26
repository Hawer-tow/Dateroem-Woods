"""Bringing the app's window to the front on Windows.

Windows lets a program put its window in front only while it is the program
in front; otherwise activateWindow() just flashes the taskbar button. The
app takes about ten seconds to load from the WSL folder, and if anything
else took the front in that time (or the window that had it closed), the
sign-in window opened behind other windows and had to be clicked in the
taskbar (26 October 2026). Used only for the window the person has just
asked for by opening the app.
"""
import sys

from PySide6.QtGui import QGuiApplication


def bring_to_front(widget) -> None:
    widget.raise_()
    widget.activateWindow()
    if sys.platform != "win32" or QGuiApplication.platformName() != "windows":
        return
    try:
        import ctypes
        from ctypes import wintypes

        user32 = ctypes.windll.user32
        kernel32 = ctypes.windll.kernel32
        user32.GetForegroundWindow.restype = wintypes.HWND
        user32.GetWindowThreadProcessId.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.DWORD)]
        user32.GetWindowThreadProcessId.restype = wintypes.DWORD
        user32.SetForegroundWindow.argtypes = [wintypes.HWND]
        user32.BringWindowToTop.argtypes = [wintypes.HWND]

        hwnd = wintypes.HWND(int(widget.winId()))
        front = user32.GetForegroundWindow()
        if front and front == hwnd.value:
            return
        this_thread = kernel32.GetCurrentThreadId()
        front_thread = user32.GetWindowThreadProcessId(front, None) if front else 0
        # Sharing input with the thread in front for a moment is the
        # documented way to be allowed to take its place.
        attached = bool(front_thread and front_thread != this_thread
                        and user32.AttachThreadInput(front_thread, this_thread, True))
        try:
            user32.BringWindowToTop(hwnd)
            user32.SetForegroundWindow(hwnd)
        finally:
            if attached:
                user32.AttachThreadInput(front_thread, this_thread, False)
    except Exception:  # noqa: BLE001 - never worth failing start-up over
        pass
