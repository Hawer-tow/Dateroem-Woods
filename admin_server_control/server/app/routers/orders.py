from fastapi import APIRouter, Header, Query

from app.core import backend_client
from app.core.auth_header import auth_header
from app.schemas.admin import DropOrderRequest

router = APIRouter(prefix="/orders", tags=["orders"])


# "add the drop option ... for a particular order for a customer you can
# add the drop and refund option so that the desktop admin can also
# refund the cash" - admin-only, enforced by the Java backend's own real
# JWT check (OrderController.requireAdmin), same reasoning as
# admin_data.py's tables browser: the Authorization header is forwarded
# through untouched rather than swapped for X-Actor-User-Id, since that's
# what the backend actually validates here.
@router.post("/{order_id}/drop")
async def drop_order(order_id: int, request: DropOrderRequest, authorization: str | None = Header(default=None)):
    return await backend_client.post(
        f"/api/orders/{order_id}/drop", json=request.model_dump(), headers=auth_header(authorization)
    )


# Dropped orders still owing their customer a refund - the same list the
# mobile app's Transactions -> Refunds screen shows, for Send Money
# (ExpenseTarget.ORDER_REFUND) to pay out.
@router.get("/refunds/pending")
async def pending_refunds(authorization: str | None = Header(default=None)):
    return await backend_client.get("/api/orders/refunds/pending", headers=auth_header(authorization))


# Every website payment with its M-Pesa receipt, newest first - the desktop's
# Insights -> Website Payments list (admin-only, checked by the backend).
@router.get("/payments")
async def payments(limit: int = Query(default=200, ge=1, le=1000), authorization: str | None = Header(default=None)):
    return await backend_client.get("/api/orders/payments", params={"limit": limit}, headers=auth_header(authorization))
