import io
from typing import Optional

from fastapi import APIRouter, File, Header, HTTPException, UploadFile
from PIL import Image, ImageOps, UnidentifiedImageError
from pydantic import BaseModel

from app.core import backend_client, profile_photos
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


# "a profile area ... updating contacts/password" (migration 039): the staff
# member's own name, email (with the password), password and photo. The
# phone number is not here - payouts go to it, so an admin changes it.
class ProfileRequest(BaseModel):
    name: Optional[str] = None
    email: Optional[str] = None
    currentPassword: Optional[str] = None


class PasswordRequest(BaseModel):
    currentPassword: Optional[str] = None
    newPassword: Optional[str] = None


def _auth(authorization: Optional[str]) -> Optional[dict]:
    return {"Authorization": authorization} if authorization else None


@router.put("/me/profile")
async def update_profile(request: ProfileRequest, authorization: Optional[str] = Header(default=None)):
    return await backend_client.put("/api/auth/me/profile", json=request.model_dump(), headers=_auth(authorization))


@router.post("/me/password")
async def change_password(request: PasswordRequest, authorization: Optional[str] = Header(default=None)):
    """Answers {token}: the phone's new sign-in - every other one ends."""
    return await backend_client.post("/api/auth/me/password", json=request.model_dump(), headers=_auth(authorization))


PHOTO_SIZE = 512


@router.post("/me/photo")
async def upload_photo(file: UploadFile = File(...), authorization: Optional[str] = Header(default=None)):
    """The profile photo from the camera or gallery: turned the right way up,
    cut to a centred square and shrunk to 512 px here, whatever was sent."""
    contents = await file.read()
    if len(contents) > 15 * 1024 * 1024:
        raise HTTPException(status_code=413, detail="That photo is too large (max 15 MB)")
    try:
        image = Image.open(io.BytesIO(contents))
        image.load()
    except UnidentifiedImageError as exc:
        raise HTTPException(status_code=400, detail="That file isn't a readable image") from exc
    image = ImageOps.fit(ImageOps.exif_transpose(image).convert("RGB"), (PHOTO_SIZE, PHOTO_SIZE))
    out = io.BytesIO()
    image.save(out, "JPEG", quality=85, optimize=True)
    me = await backend_client.get("/api/auth/me", headers=_auth(authorization))
    key = profile_photos.save(me["userId"], out.getvalue())
    try:
        result = await backend_client.put("/api/auth/me/photo", json={"photo": key}, headers=_auth(authorization))
    except Exception:
        profile_photos.remove(key)
        raise
    profile_photos.remove((result or {}).get("previous"))
    return {"photoUrl": (result or {}).get("photoUrl")}


@router.delete("/me/photo")
async def remove_photo(authorization: Optional[str] = Header(default=None)):
    result = await backend_client.put("/api/auth/me/photo", json={"photo": None}, headers=_auth(authorization))
    profile_photos.remove((result or {}).get("previous"))
    return {"photoUrl": None}
