from fastapi import APIRouter, Header
from pydantic import BaseModel

from app.core import backend_client
from app.core.auth_header import auth_header

router = APIRouter(prefix="/reviews", tags=["reviews"])


class HiddenRequest(BaseModel):
    hidden: bool


# Product ratings moderation - admin-only, enforced by the Java backend's
# own real JWT check (ReviewController.requireAdmin); the Authorization
# header is forwarded untouched, same as orders.py.
@router.get("/admin")
async def list_reviews(authorization: str | None = Header(default=None)):
    return await backend_client.get("/api/reviews/admin", headers=auth_header(authorization))


# Hiding drops an abusive/false review from the product page and from its
# stars; showing it again puts it back. The customer's own copy stays.
@router.post("/admin/{review_id}/hidden")
async def set_hidden(review_id: int, request: HiddenRequest, authorization: str | None = Header(default=None)):
    return await backend_client.post(
        f"/api/reviews/admin/{review_id}/hidden", json=request.model_dump(), headers=auth_header(authorization)
    )
