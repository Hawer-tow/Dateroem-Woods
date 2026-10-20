import logging

import httpx
from fastapi import HTTPException

from app.core.config import settings

# "in the event of an error e.g the server is offline provide the best
# proffesional way to handle this errors so the user in the website or
# mobile never gets the whole details but what is necessary and
# presentatble" (18 October 2026). What a caller is told when the backend
# can't help: a calm sentence - never an address, an exception or a stack
# trace (those go to this server's log). A reason the backend wrote for
# people still passes through: a 4xx ("Not enough stock ...") or a
# deliberate 503 ("Text messages are not set up yet.").
UNREACHABLE = "We can't reach our server right now. Please try again in a few minutes."
TOO_SLOW = "Our server is taking too long to answer. Please try again in a moment."
PROBLEM = "Something went wrong on our side. Please try again in a moment."
_PLAIN = {
    400: "Please check the details and try again.",
    401: "Please sign in again.",
    403: "You don't have access to this.",
    404: "We couldn't find that.",
    409: "That clashes with something already saved.",
    413: "That file is too large.",
    429: "Too many tries. Please wait a moment and try again.",
}
log = logging.getLogger("dateroem.backend")


def _path(exc: httpx.HTTPError) -> str:
    try:
        return exc.request.url.path
    except RuntimeError:
        return "?"


def _clean_detail(response: httpx.Response) -> str:
    """The backend's own message (Spring's error body "message"), or ''.
    Never the raw body: that is a blob with a timestamp and, in
    development, a full stack trace."""
    try:
        body = response.json()
    except ValueError:
        return ""
    if not isinstance(body, dict):
        return ""
    message = body.get("message") or body.get("detail")
    return message.strip() if isinstance(message, str) else ""


def _unreachable(exc: httpx.RequestError) -> HTTPException:
    log.warning("backend not reachable (%s) for %s", type(exc).__name__, _path(exc))
    if isinstance(exc, httpx.TimeoutException):
        return HTTPException(status_code=504, detail=TOO_SLOW)
    return HTTPException(status_code=503, detail=UNREACHABLE)


def _backend_error(exc: httpx.HTTPStatusError) -> HTTPException:
    status = exc.response.status_code
    message = _clean_detail(exc.response)
    if status >= 500:
        log.error("backend answered %s for %s: %s", status, _path(exc), message[:500])
        if status == 503 and message:
            return HTTPException(status_code=503, detail=message)
        return HTTPException(status_code=500, detail=PROBLEM)
    return HTTPException(status_code=status, detail=message or _PLAIN.get(status, PROBLEM))



def _rewrite_media_urls(value):
    # The Java backend bakes an absolute media URL into every image/media
    # field (app.media.base-url, e.g. "http://127.0.0.1:8000/media/x.jpg")
    # - correct for a request made from the same machine as the backend,
    # but meaningless once the website is opened from anywhere else (a
    # phone on another network via the ngrok tunnel: "why cant i see the
    # products images ... website wanting to access other applications
    # and services in the machine" - that prompt was Chrome's Private
    # Network Access check, triggered because the page (a public origin)
    # was trying to reach a loopback address). Rewriting it to a website-
    # relative /media/ path here means the browser always fetches media
    # from whatever origin actually served the page - this BFF's own
    # /media mount - never a hardcoded loopback IP. Scoped to this BFF
    # only: the mobile app and desktop admin app are real native/Python
    # clients with no "current page origin" to resolve a relative URL
    # against, so they still need (and get, from the backend directly)
    # the absolute form.
    if isinstance(value, dict):
        return {key: _rewrite_media_urls(v) for key, v in value.items()}
    if isinstance(value, list):
        return [_rewrite_media_urls(v) for v in value]
    if isinstance(value, str) and value.startswith(settings.media_base_url):
        return "/media/" + value[len(settings.media_base_url):]
    return value


# TODO: this is the "handle it gracefully / serve cached data if the
# backend fails" point business rules.txt asks for. Right now a backend
# outage turns into a clean 503 instead of a raw crash - real caching
# (e.g. Redis, using settings.redis_url) still needs to be layered in here
# so reads can fall back to a last-known-good value instead of failing.


async def get(path: str, params: dict | None = None, headers: dict | None = None) -> dict | list:
    async with httpx.AsyncClient(base_url=settings.backend_base_url, timeout=10.0) as client:
        try:
            response = await client.get(path, params=params, headers=headers)
            response.raise_for_status()
        except httpx.RequestError as exc:
            raise _unreachable(exc) from exc
        except httpx.HTTPStatusError as exc:
            # backend was reachable but returned an error - surface its
            # actual status instead of crashing unhandled (caught via live
            # testing: raise_for_status() raises HTTPStatusError, a sibling
            # of RequestError, not a subclass - the except above alone
            # doesn't catch it).
            raise _backend_error(exc) from exc
        return _rewrite_media_urls(response.json())


async def post(path: str, json: dict, headers: dict | None = None, timeout: float = 10.0) -> dict | list | None:
    async with httpx.AsyncClient(base_url=settings.backend_base_url, timeout=timeout) as client:
        try:
            response = await client.post(path, json=json, headers=headers)
            response.raise_for_status()
        except httpx.RequestError as exc:
            raise _unreachable(exc) from exc
        except httpx.HTTPStatusError as exc:
            raise _backend_error(exc) from exc
        return _rewrite_media_urls(response.json()) if response.content else None


async def put(path: str, json: dict, headers: dict | None = None) -> dict | list | None:
    async with httpx.AsyncClient(base_url=settings.backend_base_url, timeout=10.0) as client:
        try:
            response = await client.put(path, json=json, headers=headers)
            response.raise_for_status()
        except httpx.RequestError as exc:
            raise _unreachable(exc) from exc
        except httpx.HTTPStatusError as exc:
            raise _backend_error(exc) from exc
        return _rewrite_media_urls(response.json()) if response.content else None


async def delete(path: str, headers: dict | None = None) -> dict | list | None:
    async with httpx.AsyncClient(base_url=settings.backend_base_url, timeout=10.0) as client:
        try:
            response = await client.delete(path, headers=headers)
            response.raise_for_status()
        except httpx.RequestError as exc:
            raise _unreachable(exc) from exc
        except httpx.HTTPStatusError as exc:
            raise _backend_error(exc) from exc
        return _rewrite_media_urls(response.json()) if response.content else None
