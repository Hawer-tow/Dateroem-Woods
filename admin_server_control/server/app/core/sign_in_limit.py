"""A limit on wrong passwords at desktop sign-in.

"what are the risks how to resolve them" - once the desktop app can reach
this server from other computers, a password could otherwise be guessed as
fast as a script can try. After MAX_FAILURES wrong passwords for one account
from one address within WINDOW_SECONDS, that pair waits until the oldest of
them is WINDOW_SECONDS old. Kept per address as well as per account, so
someone typing wrong passwords elsewhere can't lock the real person out.
Held in memory: restarting this server clears it.
"""
import time

MAX_FAILURES = 5
WINDOW_SECONDS = 15 * 60
# Past this many tracked pairs, forget the ones with no recent failures.
_PRUNE_ABOVE = 5000

_failures: dict[tuple[str, str], list[float]] = {}


def _key(user_name: str, address: str) -> tuple[str, str]:
    return user_name.strip().lower(), address


def _recent(key: tuple[str, str], now: float) -> list[float]:
    recent = [moment for moment in _failures.get(key, []) if now - moment < WINDOW_SECONDS]
    if recent:
        _failures[key] = recent
    else:
        _failures.pop(key, None)
    return recent


def wait_seconds(user_name: str, address: str) -> int:
    """How long this account must wait before trying again from this address (0 = it may try now)."""
    now = time.monotonic()
    recent = _recent(_key(user_name, address), now)
    if len(recent) < MAX_FAILURES:
        return 0
    return int(WINDOW_SECONDS - (now - recent[-MAX_FAILURES])) + 1


def record_failure(user_name: str, address: str):
    now = time.monotonic()
    if len(_failures) > _PRUNE_ABOVE:
        for key in list(_failures):
            _recent(key, now)
    key = _key(user_name, address)
    _failures[key] = _recent(key, now) + [now]


def clear(user_name: str, address: str):
    _failures.pop(_key(user_name, address), None)
