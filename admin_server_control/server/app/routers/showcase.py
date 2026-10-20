import base64
import binascii
import re
import uuid
from pathlib import Path

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.core import backend_client

# The Bulk Orders page's showcase pictures (backend ShowcaseService), for the
# desktop Marketing section's "Bulk order pictures..." window. Every change
# is checked by the backend from the computer's sign-in token (Marketer,
# Admin or SuperAdmin).
router = APIRouter(prefix="/showcase", tags=["showcase"])

# Same shared folder the website serves at /media (fathersProject/media).
SHOWCASE_DIR = Path(__file__).resolve().parents[4] / "media" / "showcase"
MAX_JPEG_BYTES = 5 * 1024 * 1024
# Who may change the showcase (backend AdminGuard.requireShowcaseEditor).
SHOWCASE_EDITORS = {"Marketer", "Admin", "SuperAdmin"}


class ShowcasePicture(BaseModel):
    image: str | None = None
    caption: str | None = Field(default=None, max_length=120)


class ShowcaseOrder(BaseModel):
    showcaseIds: list[int]


class ShowcaseUpload(BaseModel):
    # The desktop app shrinks the picture to at most 1600 px and encodes it
    # as JPEG itself (Qt), then sends it here as base64.
    name: str = Field(default="bulk-order", max_length=120)
    dataBase64: str


@router.get("")
async def list_pictures():
    return await backend_client.get("/api/showcase")


@router.post("/upload")
async def upload_picture(request: ShowcaseUpload):
    """Saves an already-resized JPEG in media/showcase and returns its key,
    which the app then sends to POST /showcase. Checked first: the caller is
    signed in (the backend answers /api/auth/me only then) as someone who may
    change the showcase, and the file really is a JPEG of sensible size."""
    me = await backend_client.get("/api/auth/me")
    if (me or {}).get("roleName") not in SHOWCASE_EDITORS:
        raise HTTPException(status_code=403, detail="Marketer, Admin or SuperAdmin only")
    try:
        data = base64.b64decode(request.dataBase64, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise HTTPException(status_code=400, detail="That picture couldn't be read") from exc
    if len(data) > MAX_JPEG_BYTES:
        raise HTTPException(status_code=400, detail="Picture too large (max 5 MB after resizing)")
    if not (data.startswith(b"\xff\xd8\xff") and data.rstrip(b"\x00").endswith(b"\xff\xd9")):
        raise HTTPException(status_code=400, detail="Only JPEG pictures can be uploaded")

    slug = re.sub(r"[^a-z0-9]+", "-", request.name.strip().lower()).strip("-")[:60] or "bulk-order"
    filename = f"{slug}-{uuid.uuid4().hex[:8]}.jpg"
    SHOWCASE_DIR.mkdir(parents=True, exist_ok=True)
    (SHOWCASE_DIR / filename).write_bytes(data)
    return {"key": f"showcase/{filename}"}


@router.post("")
async def add_picture(request: ShowcasePicture):
    return await backend_client.post("/api/showcase", json=request.model_dump())


# Before /{showcase_id} so "order" isn't read as an id.
@router.put("/order")
async def reorder_pictures(request: ShowcaseOrder):
    return await backend_client.put("/api/showcase/order", json=request.model_dump())


@router.patch("/{showcase_id}")
async def edit_caption(showcase_id: int, request: ShowcasePicture):
    return await backend_client.patch(f"/api/showcase/{showcase_id}", json={"caption": request.caption})


@router.delete("/{showcase_id}")
async def remove_picture(showcase_id: int):
    await backend_client.delete(f"/api/showcase/{showcase_id}")
    return {"status": "removed"}
