from fastapi import APIRouter, Header

from app.core import backend_client
from app.schemas.auth import LoginRequest, ThemeRequest

router = APIRouter(prefix="/auth", tags=["auth"])


@router.get("/device/challenge")
async def device_challenge():
    """A one-time value the app signs with its phone key before signing in
    ("allows only one user per account per mobile phone")."""
    return await backend_client.get("/api/auth/device/challenge")


@router.post("/login")
async def login(request: LoginRequest):
    """Staff sign-in for the mobile app (employees/marketers/admins). The
    phone's proof goes along with the password: the backend only issues a
    token for a phone approved for this account - an account's first phone
    is approved automatically, any other waits for an admin."""
    payload = {"userName": request.user_name, "password": request.password}
    if request.device is not None:
        payload["device"] = request.device.model_dump()
    return await backend_client.post("/api/auth/login", json=payload)


@router.get("/me")
async def me(authorization: str | None = Header(default=None)):
    return await backend_client.get("/api/auth/me", headers={"Authorization": authorization} if authorization else None)


@router.put("/me/theme")
async def save_theme(request: ThemeRequest, authorization: str | None = Header(default=None)):
    """The signed-in person's colour theme (forest, dark or light) from the
    app's Settings; the website and the desktop app open in it too."""
    return await backend_client.put(
        "/api/auth/me/theme", json={"theme": request.theme},
        headers={"Authorization": authorization} if authorization else None,
    )
