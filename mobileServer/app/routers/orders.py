from fastapi import APIRouter, Header
from pydantic import BaseModel

from app.core import backend_client

router = APIRouter(prefix="/orders", tags=["orders"])


class DropOrderRequest(BaseModel):
    # "a cost is reduced from the initially sent by the admins" - the
    # admin types this in on the drop screen.
    cancellationCost: float


# "allow a user to drop the product if it has not yet been assigned a
# trip which will allow one to get refunded the money paid for that
# product" - admin-only, enforced by the Java backend's own real JWT check
# (OrderController.requireAdmin), not just this header - the Authorization
# header is forwarded through untouched rather than swapped for
# X-Actor-User-Id, since that's what the backend actually validates here.
@router.post("/{order_id}/drop")
async def drop_order(order_id: int, request: DropOrderRequest, authorization: str | None = Header(default=None)):
    headers = {"Authorization": authorization} if authorization else {}
    return await backend_client.post(f"/api/orders/{order_id}/drop", json=request.model_dump(), headers=headers)


# "where exactly do i go ... still cannot see it" - dropped orders still
# owing their customer a refund, for the mobile Refunds list -> Send Money
# (ExpenseTarget.ORDER_REFUND) flow.
@router.get("/refunds/pending")
async def pending_refunds(authorization: str | None = Header(default=None)):
    headers = {"Authorization": authorization} if authorization else {}
    return await backend_client.get("/api/orders/refunds/pending", headers=headers)
