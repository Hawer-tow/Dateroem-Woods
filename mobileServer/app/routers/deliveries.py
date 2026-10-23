from typing import Optional

from fastapi import APIRouter, Header
from pydantic import BaseModel

from app.core import backend_client

router = APIRouter(prefix="/deliveries", tags=["deliveries"])


class LightDeliveryStatusRequest(BaseModel):
    status: str


# "add a signature area which is stored in the signature blob for the
# said products for the person to be added to the database" - a small
# base64 PNG data URL from the phone's signature pad, plus who signed.
class DeliverLightDeliveryRequest(BaseModel):
    signature: Optional[str] = None
    receivedBy: Optional[str] = None


@router.get("/light")
async def list_light_deliveries(trip_id: Optional[int] = None):
    """Trip detail's light-deliveries drill-down (business rules: "in a
    heavy or light one can see the level details of each delivery")."""
    params = {"tripId": trip_id} if trip_id is not None else None
    return await backend_client.get("/api/deliveries/light", params=params)


@router.patch("/light/{light_delivery_id}/status")
async def update_light_delivery_status(light_delivery_id: int, request: LightDeliveryStatusRequest):
    await backend_client.patch(f"/api/deliveries/light/{light_delivery_id}/status", json=request.model_dump())
    return {"status": "updated"}


@router.patch("/light/{light_delivery_id}/deliver")
async def deliver_light_delivery(light_delivery_id: int, request: DeliverLightDeliveryRequest):
    await backend_client.patch(f"/api/deliveries/light/{light_delivery_id}/deliver", json=request.model_dump())
    return {"status": "delivered"}


class AssignBatchRequest(BaseModel):
    lightDeliveryIds: list[int]
    tripId: Optional[int] = None
    vehicleId: Optional[int] = None
    estimatedDeliveryDate: Optional[str] = None
    bulkDeliveryId: Optional[int] = None
    parentLightDeliveryId: Optional[int] = None
    # "vehicle visible only to staff" (migration 038): may the customer see
    # the driver's first name and the number plate? Off unless ticked.
    shareDriver: Optional[bool] = None


class ShareDriverRequest(BaseModel):
    share: bool


class SettleRequest(BaseModel):
    deliveredIds: list[int] = []
    undeliveredIds: list[int] = []
    reason: Optional[str] = None


class CreateLightDeliveryRequest(BaseModel):
    tripId: int
    vehicleId: Optional[int] = None
    deliveryAreaId: Optional[int] = None
    estimatedDeliveryDate: Optional[str] = None
    # "allow one to name a delivery ... a name delivery maasai allows
    # quick identification" - optional, purely cosmetic.
    deliveryName: Optional[str] = None
    shareDriver: Optional[bool] = None


# Pending orders gathered per customer per delivery area, with the goods
# gauge (LIGHT / BULK / COMBINED) and any batch the customer already has -
# the mobile Orders section, same data the desktop Insights > Orders tab uses.
@router.get("/light/pending-groups")
async def pending_order_groups():
    return await backend_client.get("/api/deliveries/light/pending-groups")


# "vehicle visible only to staff ... unless enabled" (migration 038) - Share
# driver on one delivery and the orders grouped under it.
@router.patch("/light/{light_delivery_id}/share-driver")
async def share_driver(light_delivery_id: int, request: ShareDriverRequest, x_actor_user_id: int = Header(...)):
    await backend_client.patch(
        f"/api/deliveries/light/{light_delivery_id}/share-driver",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
    )
    return {"status": "shared" if request.share else "hidden"}


@router.post("/light/assign-batch")
async def assign_batch(request: AssignBatchRequest, x_actor_user_id: int = Header(...)):
    await backend_client.post(
        "/api/deliveries/light/assign-batch", json=request.model_dump(), headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )
    return {"status": "assigned"}


@router.post("/light/settle")
async def settle_light_orders(request: SettleRequest, x_actor_user_id: int = Header(...)):
    await backend_client.post(
        "/api/deliveries/light/settle", json=request.model_dump(), headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )
    return {"status": "settled"}


@router.post("/light")
async def create_light_delivery(request: CreateLightDeliveryRequest, x_actor_user_id: int = Header(...)):
    return await backend_client.post(
        "/api/deliveries/light", json=request.model_dump(), headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )
