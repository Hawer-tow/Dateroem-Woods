from fastapi import APIRouter, Query

from app.core import backend_client
from app.schemas.admin import FaqRequest

router = APIRouter(prefix="/faqs", tags=["faqs"])

# "add this faqs in the desktop for editing adding new ones so that it
# constantly maintained" - the website's Help page questions. Admins only
# (checked by the backend); every change is in the audit log.


@router.get("")
async def list_faqs(deleted: bool = Query(default=False)):
    """Every question, hidden ones included - or only the deleted ones."""
    return await backend_client.get("/api/faqs/manage", params={"deleted": str(deleted).lower()})


@router.post("")
async def create_faq(request: FaqRequest):
    return await backend_client.post("/api/faqs", json=request.model_dump())


@router.patch("/{faq_id}")
async def update_faq(faq_id: int, request: FaqRequest):
    return await backend_client.patch(f"/api/faqs/{faq_id}", json=request.model_dump())


@router.delete("/{faq_id}")
async def delete_faq(faq_id: int):
    await backend_client.delete(f"/api/faqs/{faq_id}")
    return {"status": "deleted"}


@router.post("/{faq_id}/restore")
async def restore_faq(faq_id: int):
    return await backend_client.post(f"/api/faqs/{faq_id}/restore", json={})
