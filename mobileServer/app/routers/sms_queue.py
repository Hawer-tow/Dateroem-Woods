from fastapi import APIRouter, Header

from app.core import backend_client
from app.schemas.mobile import IngestSmsRequest

router = APIRouter(prefix="/sms-queue", tags=["sms-queue"])


# "i want the system to check the messages compare at times" - the
# company-SIM phone posts each M-Pesa SMS here as it arrives (or on a
# catch-up resync). "only one phone the company phone will have the
# ability to read the offline SMS messages" - this used to take anything
# from anyone with no authentication; the Authorization header is now
# forwarded through untouched (the app's own AuthInterceptor already
# attaches it to every request) so the Java backend's real JWT + the
# Can_Sync_Sms flag on that account can actually be checked.
@router.post("")
async def ingest_sms(request: IngestSmsRequest, authorization: str | None = Header(default=None)):
    headers = {"Authorization": authorization} if authorization else {}
    return await backend_client.post("/api/sms-queue", json=request.model_dump(), headers=headers)
