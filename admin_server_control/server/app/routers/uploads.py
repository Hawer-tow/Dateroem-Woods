import re
import uuid
from pathlib import Path

from fastapi import APIRouter, HTTPException, Query, Request

from app.core import backend_client

# "the ability to go to media and pick a new media or product when creating a
# new product since i can be having the video or photo here in the laptop" -
# product photos and campaign photos or videos chosen on the desktop's own
# computer. The desktop app shrinks a photo to at most 1600 px as a JPEG
# itself (Qt - this server has no image library) and sends the file as the
# request body, unchanged and not base64, so a video needs no extra room.
router = APIRouter(prefix="/uploads", tags=["uploads"])

# Same shared folder the website serves at /media (fathersProject/media).
MEDIA_DIR = Path(__file__).resolve().parents[4] / "media"
MAX_IMAGE_BYTES = 5 * 1024 * 1024      # after the app has shrunk it
MAX_VIDEO_BYTES = 60 * 1024 * 1024     # as on the phone app
VIDEO_EXTENSIONS = {".mp4", ".mov", ".webm", ".3gp", ".m4v"}
# The desktop app signs in Admins and the SuperAdmin only; checked again
# here because these routes write files without going through the backend.
UPLOADERS = {"Admin", "SuperAdmin"}


async def _require_uploader():
    me = await backend_client.get("/api/auth/me")
    if (me or {}).get("roleName") not in UPLOADERS:
        raise HTTPException(status_code=403, detail="Admin or SuperAdmin only")


async def _read_body(request: Request, limit: int, what: str) -> bytes:
    """The file, read in pieces and refused as soon as it passes the limit -
    never held whole when it is too big."""
    declared = request.headers.get("content-length")
    if declared and declared.isdigit() and int(declared) > limit:
        raise HTTPException(status_code=413, detail=f"{what} too large (max {limit // (1024 * 1024)} MB)")
    chunks, size = [], 0
    async for chunk in request.stream():
        size += len(chunk)
        if size > limit:
            raise HTTPException(status_code=413, detail=f"{what} too large (max {limit // (1024 * 1024)} MB)")
        chunks.append(chunk)
    data = b"".join(chunks)
    if not data:
        raise HTTPException(status_code=400, detail="No file was sent")
    return data


def _is_jpeg(data: bytes) -> bool:
    return data.startswith(b"\xff\xd8\xff") and data.rstrip(b"\x00").endswith(b"\xff\xd9")


def _is_video(data: bytes, extension: str) -> bool:
    if extension == ".webm":
        return data.startswith(b"\x1a\x45\xdf\xa3")
    # mp4, mov, m4v and 3gp are all ISO media files: "ftyp" at byte 4.
    return data[4:8] == b"ftyp"


def _save(folder: str, name: str, fallback: str, extension: str, data: bytes) -> str:
    """Named after the product or campaign plus a short random suffix, like
    the phone app's uploads; returns the key the backend stores."""
    slug = re.sub(r"[^a-z0-9]+", "-", (name or "").strip().lower()).strip("-")[:60] or fallback
    filename = f"{slug}-{uuid.uuid4().hex[:8]}{extension}"
    target = MEDIA_DIR / folder
    target.mkdir(parents=True, exist_ok=True)
    (target / filename).write_bytes(data)
    return f"{folder}/{filename}"


@router.post("/products")
async def upload_product_photo(request: Request, name: str = Query(..., max_length=200)):
    """A product photo (JPEG, already shrunk by the app); returns its key for
    the product form."""
    await _require_uploader()
    data = await _read_body(request, MAX_IMAGE_BYTES, "Photo")
    if not _is_jpeg(data):
        raise HTTPException(status_code=400, detail="Only JPEG photos can be uploaded")
    return {"key": _save("products", name, "product", ".jpg", data)}


@router.post("/campaigns")
async def upload_campaign_media(
    request: Request,
    name: str = Query(..., max_length=200),
    kind: str = Query(..., pattern="^(image|video)$"),
    extension: str = Query(".jpg", max_length=5),
):
    """A campaign's one media item: a photo (JPEG, already shrunk) or a video
    (mp4, mov, webm, 3gp or m4v up to 60 MB, kept as it is). Returns its key
    and mediaType for the campaign form."""
    await _require_uploader()
    if kind == "image":
        data = await _read_body(request, MAX_IMAGE_BYTES, "Photo")
        if not _is_jpeg(data):
            raise HTTPException(status_code=400, detail="Only JPEG photos can be uploaded")
        return {"key": _save("campaigns", name, "campaign", ".jpg", data), "mediaType": "IMAGE"}

    extension = extension.lower()
    if extension not in VIDEO_EXTENSIONS:
        raise HTTPException(status_code=400, detail="Videos must be mp4, mov, webm, 3gp or m4v")
    data = await _read_body(request, MAX_VIDEO_BYTES, "Video")
    if not _is_video(data, extension):
        raise HTTPException(status_code=400, detail="That file is not a video of the kind its name says")
    return {"key": _save("campaigns", name, "campaign", extension, data), "mediaType": "VIDEO"}
