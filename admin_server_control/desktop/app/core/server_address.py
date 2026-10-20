"""Where the desktop app finds its server.

"so that the app is not attached to the backend so that in the day i move to
the provider its a matter of changing the addresses port etc" - nothing about
the address is built into the app. It lives in a small settings file in this
Windows user's AppData folder and is changed from the sign-in screen
(Server: ... Change). Until then it is this computer's own admin server.

Plain http:// is accepted only for this computer and the local network;
anything on the internet must be https://, so passwords and data never
travel unencrypted.
"""
import ipaddress
import json
import os
from pathlib import Path
from urllib.parse import urljoin, urlsplit

# 127.0.0.1 rather than "localhost": Windows tries localhost's IPv6 address
# first, and that route into WSL can hang until the request times out.
DEFAULT = "http://127.0.0.1:8002"
SETTINGS_FILE = Path(os.environ.get("APPDATA") or Path.home()) / "Dateroem Admin Control" / "settings.json"
# The copy given to other admins starts at the public address instead: the
# packaging script writes it into this file (the owner's copy has none).
BUNDLED_FILE = Path(__file__).with_name("default_server.txt")

_current: str | None = None


def current() -> str:
    global _current
    if _current is None:
        _current = _read() or _bundled() or DEFAULT
    return _current


def save(address: str):
    global _current
    address = _normalized(address)
    SETTINGS_FILE.parent.mkdir(parents=True, exist_ok=True)
    settings = _read_all()
    settings["server"] = address
    SETTINGS_FILE.write_text(json.dumps(settings, indent=2), encoding="utf-8")
    _current = address


def problem(address: str) -> str | None:
    """Why this address can't be used, or None if it can."""
    parts = urlsplit(_normalized(address))
    if parts.scheme not in ("http", "https") or not parts.hostname:
        return "Enter an address like https://admin.example.com or http://localhost:8002."
    if parts.scheme == "http" and not _local_network(parts.hostname):
        return "Use https:// for an address on the internet - passwords must not travel unencrypted."
    return None


def is_this_computer(address: str | None = None) -> bool:
    """True when the server runs on this computer (the owner's setup), which
    is the only time the Servers panel can start and stop anything."""
    host = urlsplit(address or current()).hostname or ""
    return host in ("localhost", "127.0.0.1", "::1")


def absolute(path_or_url: str) -> str:
    """The server hands out pictures as paths relative to itself
    ("media/products/x.jpg"); this makes one loadable from wherever the
    server is."""
    if path_or_url.startswith(("http://", "https://")):
        return path_or_url
    return urljoin(current() + "/", path_or_url)


def _normalized(address: str) -> str:
    address = (address or "").strip().rstrip("/")
    if address and "://" not in address:
        address = "https://" + address
    return address


def _local_network(host: str) -> bool:
    if host in ("localhost",):
        return True
    try:
        ip = ipaddress.ip_address(host)
    except ValueError:
        return False
    return ip.is_loopback or ip.is_private or ip.is_link_local


def _read_all() -> dict:
    try:
        data = json.loads(SETTINGS_FILE.read_text(encoding="utf-8"))
        return data if isinstance(data, dict) else {}
    except (OSError, ValueError):
        return {}


def _read() -> str | None:
    address = _read_all().get("server")
    return _normalized(address) if isinstance(address, str) and address.strip() and problem(address) is None else None


def _bundled() -> str | None:
    try:
        address = BUNDLED_FILE.read_text(encoding="utf-8").strip()
    except OSError:
        return None
    return _normalized(address) if address and problem(address) is None else None
