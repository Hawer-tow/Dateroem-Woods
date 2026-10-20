from fastapi import APIRouter

from app.core import backend_client

router = APIRouter(prefix="/sms-queue", tags=["sms-queue"])


# Admin-side visibility into the reconciliation feature: watching messages
# land and get matched/queued while testing it, without needing the phone
# in hand. Ingestion itself always comes from the phone (see mobileServer's
# sms_queue router) - nothing here writes a new message.
@router.get("")
async def list_sms_queue(status: str | None = None):
    params = {"status": status} if status else None
    return await backend_client.get("/api/sms-queue", params=params)


@router.post("/reconcile")
async def reconcile_sms_queue():
    return await backend_client.post("/api/sms-queue/reconcile", json={})
