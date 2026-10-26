from fastapi import APIRouter, Query

from app.core import backend_client
from app.schemas.admin import NoticeRequest

router = APIRouter(prefix="/notices", tags=["notices"])

# "we might be having an update soon or new features we can write them then
# we send them with a deadline for elapsing" - the website's Updates.
# Admins only (checked by the backend); every change is in the audit log.


@router.get("")
async def list_notices(deleted: bool = Query(default=False)):
    """Every notice, drafts and ended ones included - or only the deleted ones."""
    return await backend_client.get("/api/notices/manage", params={"deleted": str(deleted).lower()})


@router.post("")
async def create_notice(request: NoticeRequest):
    return await backend_client.post("/api/notices", json=request.model_dump())


@router.patch("/{notice_id}")
async def update_notice(notice_id: int, request: NoticeRequest):
    return await backend_client.patch(f"/api/notices/{notice_id}", json=request.model_dump())


@router.delete("/{notice_id}")
async def delete_notice(notice_id: int):
    await backend_client.delete(f"/api/notices/{notice_id}")
    return {"status": "deleted"}


@router.post("/{notice_id}/restore")
async def restore_notice(notice_id: int):
    return await backend_client.post(f"/api/notices/{notice_id}/restore", json={})
