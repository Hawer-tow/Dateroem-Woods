import re

from PySide6.QtCore import QObject, Signal

from app.core import http, server_address
from app.core.computer_key import ComputerKey, ComputerKeyError
from app.core.session import Session
from app.core.version import APP_VERSION


class ApiError(Exception):
    def __init__(self, status_code: int, detail: str):
        self.status_code = status_code
        self.detail = detail
        super().__init__(f"HTTP {status_code}: {detail}" if status_code else detail)


class ApiSignals(QObject):
    # A signed-in call was answered "sign in again": the working-day
    # sign-in ran out, or this computer / the account was blocked. main.py
    # goes back to the sign-in screen with the message.
    session_ended = Signal(str)


def readable(message: str) -> str:
    """An error as people should see it: without "HTTP 403: " and the
    machine-readable code ("COMPUTER_PENDING: ") in front."""
    return re.sub(r"^(HTTP \d+: )?([A-Z_]{4,}: )?", "", message)


class ApiClient:
    """Talks to admin_server_control's own FastAPI layer, which proxies to
    the Java backend - same shape as the website/mobile app's BFFs, just for
    this desktop client. The server's address is a setting
    (core/server_address.py), not part of the app, so the servers can move
    to a hosting provider without a new copy of the app.

    Every call carries the sign-in token; the backend takes the acting user
    from it and only accepts tokens issued to an approved computer (see
    login()). actor=True still adds X-Actor-User-Id for the older mutating
    endpoints that read it, but the backend now replaces it with the
    signed-in user, so it can't be used to act as someone else."""

    def __init__(self, session: Session, computer_key: ComputerKey | None = None):
        self.session = session
        self.computer_key = computer_key or ComputerKey()
        self.signals = ApiSignals()

    def _headers(self, actor: bool = False) -> dict:
        headers = {}
        if self.session.token:
            headers["Authorization"] = f"Bearer {self.session.token}"
        if actor and self.session.user_id is not None:
            headers["X-Actor-User-Id"] = str(self.session.user_id)
        return headers

    def _request(
        self, method: str, path: str, params: dict | None = None, json: dict | None = None, actor: bool = False,
        timeout: float = 20, data: bytes | None = None, content_type: str | None = None,
    ):
        import requests  # loaded on first use - see core/http.py

        base = server_address.current()
        signed_in = self.session.token is not None
        headers = self._headers(actor)
        if content_type:
            headers["Content-Type"] = content_type
        try:
            response = http.session().request(
                method, f"{base}{path}", params=params, json=json, data=data, headers=headers, timeout=timeout
            )
        except requests.RequestException as exc:
            raise ApiError(0, f"Couldn't reach the server at {base}: {exc}") from exc

        if not response.ok:
            detail = response.text
            try:
                body = response.json()
                if isinstance(body, dict):
                    detail = body.get("detail", detail)
            except ValueError:
                pass
            if response.status_code == 401 and signed_in and self.session.token is not None:
                self.signals.session_ended.emit(readable(str(detail)))
            raise ApiError(response.status_code, detail)

        return response.json() if response.content else None

    def get(self, path: str, params: dict | None = None, actor: bool = False):
        return self._request("GET", path, params=params, actor=actor)

    def post(self, path: str, json: dict | None = None, actor: bool = False, timeout: float = 20):
        return self._request("POST", path, json=json or {}, actor=actor, timeout=timeout)

    def post_file(self, path: str, data: bytes, content_type: str, params: dict | None = None, timeout: float = 300):
        """Sends a file as the request body (a photo or video from this
        computer); a long wait is allowed for a video over the internet."""
        return self._request("POST", path, params=params, data=data, content_type=content_type, timeout=timeout)

    def patch(self, path: str, json: dict | None = None, actor: bool = False):
        return self._request("PATCH", path, json=json or {}, actor=actor)

    def put(self, path: str, json: dict | None = None, actor: bool = False):
        return self._request("PUT", path, json=json or {}, actor=actor)

    def delete(self, path: str, actor: bool = False):
        return self._request("DELETE", path, actor=actor)

    def login(self, user_name: str, password: str) -> dict:
        # The computer proves which one it is by signing a one-time
        # challenge with its own key (core/computer_key.py); the backend
        # only signs in Admin/SuperAdmin accounts on approved computers.
        challenge = self.get("/auth/computer/challenge")["challenge"]
        try:
            computer = self.computer_key.proof(challenge, APP_VERSION)
        except ComputerKeyError as exc:
            raise ApiError(0, str(exc)) from exc
        data = self.post("/auth/login", json={"user_name": user_name, "password": password, "computer": computer})
        self.session.apply_login(data)
        return data
