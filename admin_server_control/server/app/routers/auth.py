import math
import re

from fastapi import APIRouter, Header, HTTPException, Request

from app.core import backend_client, sign_in_limit
from app.core.auth_header import auth_header
from app.core.config import settings
from app.schemas.admin import LoginRequest, ThemeRequest

router = APIRouter(prefix="/auth", tags=["auth"])

TOO_OLD = "This copy of the desktop app is too old - please install the new one."


@router.get("/computer/challenge")
async def computer_challenge():
    """A one-time value the app signs with its computer key before signing
    in (see the backend's ComputerService)."""
    return await backend_client.get("/api/auth/computer/challenge")


@router.post("/login")
async def login(request: LoginRequest, http_request: Request):
    """Desktop control app sign-in - Admin and SuperAdmin accounts only, on
    an approved computer (both enforced by the backend, which issues a
    token tied to that computer). An old copy of the app (no computer proof,
    or older than min_desktop_version) is told to update; too many wrong
    passwords and the account waits (core/sign_in_limit.py)."""
    if request.computer is None or _version(request.computer.appVersion) < _version(settings.min_desktop_version):
        raise HTTPException(status_code=426, detail=TOO_OLD)
    address = _client_address(http_request)
    wait = sign_in_limit.wait_seconds(request.user_name, address)
    if wait:
        minutes = math.ceil(wait / 60)
        raise HTTPException(
            status_code=429,
            detail=f"Too many wrong passwords. Try again in {minutes} minute{'s' if minutes != 1 else ''}.",
        )
    payload = {"userName": request.user_name, "password": request.password, "computer": request.computer.model_dump()}
    try:
        data = await backend_client.post("/api/auth/login", json=payload)
    except HTTPException as exc:
        if exc.status_code == 401 and str(exc.detail).startswith("Invalid username or password"):
            sign_in_limit.record_failure(request.user_name, address)
        raise
    sign_in_limit.clear(request.user_name, address)
    return data


@router.get("/me")
async def me(authorization: str | None = Header(default=None)):
    return await backend_client.get("/api/auth/me", headers=auth_header(authorization))


@router.put("/me/theme")
async def save_theme(request: ThemeRequest, authorization: str | None = Header(default=None)):
    """The signed-in admin's colour theme (forest, dark or light) from the
    desktop's Settings; the website and the phone app open in it too."""
    return await backend_client.put("/api/auth/me/theme", json={"theme": request.theme}, headers=auth_header(authorization))


def _version(text: str | None) -> tuple[int, ...]:
    # "1.1" -> (1, 1); anything unreadable counts as oldest.
    return tuple(int(part) for part in re.findall(r"\d+", text or "")[:3]) or (0,)


def _client_address(request: Request) -> str:
    # Behind Caddy (on this same machine) the visitor's address arrives in
    # X-Forwarded-For; anyone else's own address is used as it is, so a
    # caller can't dodge the limit by sending that header themselves.
    host = request.client.host if request.client else ""
    forwarded = request.headers.get("x-forwarded-for")
    if forwarded and host in ("127.0.0.1", "::1"):
        return forwarded.split(",")[0].strip()
    return host
