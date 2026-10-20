import io
import re
import uuid
from pathlib import Path

from fastapi import APIRouter, File, HTTPException, Query, UploadFile
from PIL import Image, ImageOps, UnidentifiedImageError

from app.core import backend_client

router = APIRouter(prefix="/uploads", tags=["uploads"])

# Same shared directory frontend serves at /media (fathersProject/media) -
# writing here means the website and this app see an uploaded photo
# without any sync step.
MEDIA_DIR = Path(__file__).resolve().parent.parent.parent.parent / "media"
PRODUCTS_DIR = MEDIA_DIR / "products"
CAMPAIGNS_DIR = MEDIA_DIR / "campaigns"
SHOWCASE_DIR = MEDIA_DIR / "showcase"
# Who may change the Bulk Orders showcase (backend AdminGuard.requireShowcaseEditor).
SHOWCASE_EDITORS = {"Marketer", "Admin", "SuperAdmin"}

MAX_UPLOAD_BYTES = 15 * 1024 * 1024  # raw upload ceiling, before resizing
MAX_DIMENSION = 1600
JPEG_QUALITY = 85

MAX_VIDEO_BYTES = 60 * 1024 * 1024
ALLOWED_VIDEO_EXTENSIONS = {".mp4", ".mov", ".webm", ".3gp", ".m4v"}


def slugify(name: str, fallback: str = "media") -> str:
    """"the product name part of the product be used to name the image
    that goes into the system storage" / campaigns: "the naming of the
    image or the video will depend on the campaign name". Lowercased,
    non-alphanumeric collapsed to single hyphens, capped so a long name
    doesn't produce an unwieldy filename - a short random suffix (added by
    each caller) still keeps it unique even if two names collapse to the
    same slug."""
    slug = re.sub(r"[^a-z0-9]+", "-", name.strip().lower()).strip("-")
    return slug[:60] or fallback


async def _require_approved_phone():
    """Uploads are saved here without ever reaching the backend, so check
    first that the caller is signed in on an approved phone (the backend
    answers /api/auth/me only for such a token) - otherwise anyone who
    found this address could fill the media folder."""
    await backend_client.get("/api/auth/me")


@router.post("/products")
async def upload_product_image(file: UploadFile = File(...), name: str = Query(...)):
    """Mobile product form: "take a picture ... even after creation you
    can still update the picture." Every photo is decoded, EXIF-rotated,
    downscaled and re-encoded as JPEG here - not just trusting the client
    to have already done that (a raw camera capture from the Kotlin app's
    first cut of this feature was 8MB and silently failed to load on
    mobile over the dev USB tunnel; the app now compresses before upload,
    but this is the safety net for any client that doesn't, e.g. a future
    desktop admin tool or a direct API call). Also doubles as a basic
    content check: Pillow rejects anything that isn't decodable as a real
    image, regardless of what extension it was uploaded with.

    The stored file is now named after the product (business decision:
    product name is required precisely so this always has something real
    to name the file after), not a bare random id."""
    await _require_approved_phone()
    contents = await file.read()
    if len(contents) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=400, detail="Image too large (max 15MB)")

    try:
        image = Image.open(io.BytesIO(contents))
        image.load()
    except UnidentifiedImageError as exc:
        raise HTTPException(status_code=400, detail="That file isn't a readable image") from exc

    image = ImageOps.exif_transpose(image)
    image.thumbnail((MAX_DIMENSION, MAX_DIMENSION))
    image = image.convert("RGB")

    PRODUCTS_DIR.mkdir(parents=True, exist_ok=True)
    filename = f"{slugify(name, fallback='product')}-{uuid.uuid4().hex[:8]}.jpg"
    image.save(PRODUCTS_DIR / filename, "JPEG", quality=JPEG_QUALITY)

    return {"key": f"products/{filename}"}


@router.post("/showcase")
async def upload_showcase_picture(file: UploadFile = File(...), name: str = Query(default="bulk-order")):
    """Bulk Orders showcase picture: decoded, EXIF-rotated, downscaled to
    1600 px and re-encoded as JPEG exactly like a product photo, but kept in
    its own folder (media/showcase) - these are never product pictures.
    The returned key is then sent to POST /showcase, where the backend
    checks the person may change the showcase. The role is checked here
    too, so no one else can leave files in the folder."""
    me = await backend_client.get("/api/auth/me")
    if (me or {}).get("roleName") not in SHOWCASE_EDITORS:
        raise HTTPException(status_code=403, detail="Marketer, Admin or SuperAdmin only")
    contents = await file.read()
    if len(contents) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=400, detail="Image too large (max 15MB)")
    try:
        image = Image.open(io.BytesIO(contents))
        image.load()
    except UnidentifiedImageError as exc:
        raise HTTPException(status_code=400, detail="That file isn't a readable image") from exc

    image = ImageOps.exif_transpose(image)
    image.thumbnail((MAX_DIMENSION, MAX_DIMENSION))
    image = image.convert("RGB")

    SHOWCASE_DIR.mkdir(parents=True, exist_ok=True)
    filename = f"{slugify(name, fallback='bulk-order')}-{uuid.uuid4().hex[:8]}.jpg"
    image.save(SHOWCASE_DIR / filename, "JPEG", quality=JPEG_QUALITY)
    return {"key": f"showcase/{filename}"}


@router.post("/campaigns")
async def upload_campaign_media(file: UploadFile = File(...), name: str = Query(...)):
    """Campaign creation: "lets allow both video or image also ... in
    campaign only one of the medias can be used if its video its video if
    its image then so". One upload slot, either kind - detected from what
    was actually sent (video by extension allow-list, image by Pillow
    successfully decoding it) rather than requiring the caller to say
    which in advance, so switching media types on edit is just uploading
    a different file. Stored filename is derived from the campaign name,
    same as products."""
    await _require_approved_phone()
    contents = await file.read()
    suffix = Path(file.filename or "").suffix.lower()
    slug = slugify(name, fallback="campaign")

    if suffix in ALLOWED_VIDEO_EXTENSIONS:
        if len(contents) > MAX_VIDEO_BYTES:
            raise HTTPException(status_code=400, detail="Video too large (max 60MB)")
        CAMPAIGNS_DIR.mkdir(parents=True, exist_ok=True)
        filename = f"{slug}-{uuid.uuid4().hex[:8]}{suffix}"
        (CAMPAIGNS_DIR / filename).write_bytes(contents)
        return {"key": f"campaigns/{filename}", "mediaType": "VIDEO"}

    if len(contents) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=400, detail="Image too large (max 15MB)")
    try:
        image = Image.open(io.BytesIO(contents))
        image.load()
    except UnidentifiedImageError as exc:
        raise HTTPException(
            status_code=400,
            detail=f"Unsupported file type: {suffix or 'unknown'} (use a video - mp4/mov/webm/3gp/m4v - or an image)",
        ) from exc

    image = ImageOps.exif_transpose(image)
    image.thumbnail((MAX_DIMENSION, MAX_DIMENSION))
    image = image.convert("RGB")

    CAMPAIGNS_DIR.mkdir(parents=True, exist_ok=True)
    filename = f"{slug}-{uuid.uuid4().hex[:8]}.jpg"
    image.save(CAMPAIGNS_DIR / filename, "JPEG", quality=JPEG_QUALITY)

    return {"key": f"campaigns/{filename}", "mediaType": "IMAGE"}
