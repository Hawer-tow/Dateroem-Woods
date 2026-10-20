import logging

import httpx
from fastapi import HTTPException

from app.core.config import settings
from app.core.request_context import incoming_authorization

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


# Same shape as frontend's/mobileServer's backend_client - a thin proxy to
# the Java backend, one function per HTTP verb, uniform error handling.


def _headers(headers: dict | None) -> dict:
    """Headers for a backend call: marked as coming from the desktop app
    (the backend then requires a token issued to an approved computer and
    takes the acting user from it - see DesktopClientFilter) and carrying
    the desktop app's own sign-in token. Anything a route passes explicitly
    wins, except the X-Client mark itself."""
    merged = {}
    authorization = incoming_authorization.get()
    if authorization:
        merged["Authorization"] = authorization
    if headers:
        merged.update({key: value for key, value in headers.items() if value is not None})
    merged["X-Client"] = "desktop"
    return merged


def _rewrite_media_urls(value):
    # The backend bakes its own loopback media address into every image /
    # media field (app.media.base-url, "http://127.0.0.1:8000/media/x.jpg")
    # - only reachable on the machine the servers run on. Same fix as the
    # mobile server: a path relative to this server ("media/x.jpg", served
    # by main.py's /media mount), which the desktop app resolves against
    # whatever address it reaches this server at. The backend turns any of
    # these forms back into the stored key when a form sends one back
    # (MediaUrls.toKey).
    if isinstance(value, dict):
        return {key: _rewrite_media_urls(v) for key, v in value.items()}
    if isinstance(value, list):
        return [_rewrite_media_urls(v) for v in value]
    if isinstance(value, str) and value.startswith(settings.media_base_url):
        return "media/" + value[len(settings.media_base_url):]
    return value


async def get(path: str, params: dict | None = None, headers: dict | None = None) -> dict | list:
    async with httpx.AsyncClient(base_url=settings.backend_base_url, timeout=15.0) as client:
        try:
            response = await client.get(path, params=params, headers=_headers(headers))
            response.raise_for_status()
        except httpx.RequestError as exc:
            raise _unreachable(exc) from exc
        except httpx.HTTPStatusError as exc:
            raise _backend_error(exc) from exc
        return _rewrite_media_urls(response.json())


async def post(
    path: str, json: dict, params: dict | None = None, headers: dict | None = None, timeout: float = 15.0
) -> dict | list | None:
    # timeout is overridable - the AI handwriting-scan proxy needs longer
    # than every other endpoint here (a vision model call routinely takes
    # several seconds), while everything else keeps the same 15s default.
    async with httpx.AsyncClient(base_url=settings.backend_base_url, timeout=timeout) as client:
        try:
            response = await client.post(path, json=json, params=params, headers=_headers(headers))
            response.raise_for_status()
        except httpx.RequestError as exc:
            raise _unreachable(exc) from exc
        except httpx.HTTPStatusError as exc:
            raise _backend_error(exc) from exc
        return _rewrite_media_urls(response.json()) if response.content else None


async def patch(path: str, json: dict, params: dict | None = None, headers: dict | None = None) -> dict | list | None:
    async with httpx.AsyncClient(base_url=settings.backend_base_url, timeout=15.0) as client:
        try:
            response = await client.patch(path, json=json, params=params, headers=_headers(headers))
            response.raise_for_status()
        except httpx.RequestError as exc:
            raise _unreachable(exc) from exc
        except httpx.HTTPStatusError as exc:
            raise _backend_error(exc) from exc
        return _rewrite_media_urls(response.json()) if response.content else None


async def put(path: str, json: dict, params: dict | None = None, headers: dict | None = None) -> dict | list | None:
    async with httpx.AsyncClient(base_url=settings.backend_base_url, timeout=15.0) as client:
        try:
            response = await client.put(path, json=json, params=params, headers=_headers(headers))
            response.raise_for_status()
        except httpx.RequestError as exc:
            raise _unreachable(exc) from exc
        except httpx.HTTPStatusError as exc:
            raise _backend_error(exc) from exc
        return _rewrite_media_urls(response.json()) if response.content else None


async def delete(path: str, params: dict | None = None, headers: dict | None = None) -> None:
    async with httpx.AsyncClient(base_url=settings.backend_base_url, timeout=15.0) as client:
        try:
            response = await client.delete(path, params=params, headers=_headers(headers))
            response.raise_for_status()
        except httpx.RequestError as exc:
            raise _unreachable(exc) from exc
        except httpx.HTTPStatusError as exc:
            raise _backend_error(exc) from exc
