from typing import Optional

from fastapi import APIRouter, Header

from app.core import backend_client
from app.schemas.mobile import CreateBulkOrderRequest, FinalizeBulkDeliveryRequest

router = APIRouter(prefix="/bulk-orders", tags=["bulk-orders"])


@router.get("")
async def list_bulk_orders(trip_id: Optional[int] = None):
    """Heavy delivery orders search page."""
    params = {"tripId": trip_id} if trip_id is not None else None
    return await backend_client.get("/api/deliveries/bulk", params=params)


@router.get("/{bulk_delivery_id}")
async def get_bulk_order(bulk_delivery_id: int):
    return await backend_client.get(f"/api/deliveries/bulk/{bulk_delivery_id}")


@router.get("/{bulk_delivery_id}/production")
async def bulk_order_production(bulk_delivery_id: int):
    """The delivered product's details, once finalized."""
    return await backend_client.get(f"/api/deliveries/bulk/{bulk_delivery_id}/production")


@router.post("")
async def create_bulk_order(request: CreateBulkOrderRequest, x_actor_user_id: int = Header(...)):
    """"i want to allow the admin or staff to create a bulk order and its
    added into the system" - opens a paired ORDERS + BULK_DELIVERY record
    for a heavy-order negotiation (mostly offline)."""
    return await backend_client.post(
        "/api/deliveries/bulk", json=request.model_dump(), headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )


@router.delete("/{bulk_delivery_id}")
async def delete_bulk_order(bulk_delivery_id: int, x_actor_user_id: int = Header(...)):
    """"if it fails its just deleted" - the negotiation didn't proceed."""
    await backend_client.delete(
        f"/api/deliveries/bulk/{bulk_delivery_id}", headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )
    return {"status": "deleted"}


@router.patch("/{bulk_delivery_id}/finalize")
async def finalize_bulk_order(bulk_delivery_id: int, request: FinalizeBulkDeliveryRequest, x_actor_user_id: int = Header(...)):
    """"once the bulk delivery is delivered the details are filled on
    amount cash soft ... as well as the bulk delivery description table"."""
    await backend_client.patch(
        f"/api/deliveries/bulk/{bulk_delivery_id}/finalize",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
    )
    return {"status": "delivered"}
