"""Every HTTP call the desktop app makes goes through session() here.

https:// requests go through WinHTTP, Windows' own HTTP service (see
core/winhttp.py for why); plain http:// to this computer or the local
network keeps requests' own transport. Callers use requests as usual -
session().request(...), session().get(...) - and get a normal
requests.Response or requests' own exceptions.

The session is built on first use rather than when the app starts:
"opening ... starting ... it does not go away from the screen" - loading
requests and everything it brings took about five of the ten seconds the
"Starting..." window was stuck on screen. main.py loads it in the
background while the sign-in screen is up (warm_up).
"""
import sys
import threading

_session = None
_lock = threading.Lock()


def session():
    global _session
    with _lock:
        if _session is None:
            import requests

            built = requests.Session()
            if sys.platform == "win32":
                from app.core.winhttp import WinHttpAdapter

                built.mount("https://", WinHttpAdapter())
            _session = built
    return _session


def warm_up():
    """Builds the session on a helper thread, so the first sign-in doesn't
    wait for it."""
    threading.Thread(target=session, name="http-warm-up", daemon=True).start()
