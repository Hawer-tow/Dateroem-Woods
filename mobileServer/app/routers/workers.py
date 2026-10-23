import base64
import io
from typing import Optional

from fastapi import APIRouter, File, Header, HTTPException, UploadFile
from PIL import Image, ImageOps, UnidentifiedImageError
from pydantic import BaseModel

from app.core import backend_client

router = APIRouter(prefix="/workers", tags=["workers"])

# A document photo is kept readable but small: turned upright and at most
# 1600 px, as a JPEG (the backend encrypts it).
DOC_MAX_DIMENSION = 1600


class WorkerValuesRequest(BaseModel):
    values: dict[str, Optional[str]] = {}


def _auth(authorization: Optional[str]) -> Optional[dict]:
    return {"Authorization": authorization} if authorization else None


# "worker ID number and documents" (migration 040): the staff member's own
# details and document photos, from My profile on the phone. Only the
# SuperAdmin can open them afterwards (the desktop app).
@router.get("/me")
async def my_details(authorization: Optional[str] = Header(default=None)):
    return await backend_client.get("/api/workers/me", headers=_auth(authorization))


@router.put("/me")
async def save_my_details(request: WorkerValuesRequest, authorization: Optional[str] = Header(default=None)):
    return await backend_client.put("/api/workers/me", json=request.model_dump(), headers=_auth(authorization))


@router.post("/me/documents/{type_key}")
async def upload_my_document(type_key: str, file: UploadFile = File(...), authorization: Optional[str] = Header(default=None)):
    contents = await file.read()
    if len(contents) > 15 * 1024 * 1024:
        raise HTTPException(status_code=413, detail="That photo is too large (max 15 MB)")
    try:
        image = Image.open(io.BytesIO(contents))
        image.load()
    except UnidentifiedImageError as exc:
        raise HTTPException(status_code=400, detail="That file isn't a readable image") from exc
    image = ImageOps.exif_transpose(image).convert("RGB")
    image.thumbnail((DOC_MAX_DIMENSION, DOC_MAX_DIMENSION))
    out = io.BytesIO()
    image.save(out, "JPEG", quality=85, optimize=True)
    return await backend_client.post(
        f"/api/workers/me/documents/{type_key}",
        json={"data": base64.b64encode(out.getvalue()).decode()},
        headers=_auth(authorization),
    )
