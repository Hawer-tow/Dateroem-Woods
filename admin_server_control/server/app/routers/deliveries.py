from fastapi import APIRouter, Header

from app.core import backend_client
from app.schemas.admin import (
    AssignLightDeliveriesBatchRequest,
    AssignLightDeliveryRequest,
    CreateBulkDeliveryRequest,
    CreateLightDeliveryRequest,
    FinalizeBulkDeliveryRequest,
    LightDeliveryStatusRequest,
    ProductionDescriptionRequest,
    ScanImageRequest,
    SettleLightOrdersRequest,
    ShareDriverRequest,
)

router = APIRouter(prefix="/deliveries", tags=["deliveries"])


@router.get("/light")
async def list_light_deliveries(trip_id: int | None = None):
    params = {"tripId": trip_id} if trip_id is not None else None
    return await backend_client.get("/api/deliveries/light", params=params)


# "if it's heading to the same direction and there is space" - pending
# orders not yet attached to an outgoing trip, for an admin to pick from.
# Declared before /light/{light_delivery_id}-shaped routes for the same
# reason /mine sits before /{order_id} on the website's orders router.
# "some of these orders may be from the same person" - the pending pool
# folded into one group per customer per area, with LIGHT/BULK/COMBINED.
@router.get("/light/pending-groups")
async def pending_order_groups():
    return await backend_client.get("/api/deliveries/light/pending-groups")


# A whole group (or the orders left ticked) into one delivery in one call.
@router.post("/light/assign-batch")
async def assign_light_batch(request: AssignLightDeliveriesBatchRequest, x_actor_user_id: int = Header(...)):
    await backend_client.post(
        "/api/deliveries/light/assign-batch", json=request.model_dump(), headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )
    return {"status": "assigned"}


@router.get("/light/unassigned")
async def unassigned_light_deliveries():
    return await backend_client.get("/api/deliveries/light/unassigned")


# "in desktop in both deliveries allow one to create one" - opens a light
# delivery directly on a trip; pending orders are then assigned under it.
@router.post("/light")
async def create_light_delivery(request: CreateLightDeliveryRequest, x_actor_user_id: int = Header(...)):
    return await backend_client.post(
        "/api/deliveries/light", json=request.model_dump(), headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )


# "any product that was not delivered ... will be dropped for
# reassignment in another delivery" - closes out the delivered orders and
# sends the rest back to the pending pool flagged as priority.
@router.post("/light/settle")
async def settle_light_orders(request: SettleLightOrdersRequest, x_actor_user_id: int = Header(...)):
    await backend_client.post(
        "/api/deliveries/light/settle", json=request.model_dump(), headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )
    return {"status": "settled"}


# Same for bulk (the backend endpoint mobile already uses).
@router.post("/bulk")
async def create_bulk_delivery(request: CreateBulkDeliveryRequest, x_actor_user_id: int = Header(...)):
    return await backend_client.post(
        "/api/deliveries/bulk", json=request.model_dump(), headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )


@router.patch("/light/{light_delivery_id}/status")
async def update_light_delivery_status(light_delivery_id: int, request: LightDeliveryStatusRequest):
    await backend_client.patch(f"/api/deliveries/light/{light_delivery_id}/status", json=request.model_dump())
    return {"status": "updated"}


# "the assigned delivery since it has a delivery end the customer in the
# ordered goods sees will be delivered on 21st" - links the order's
# delivery to a real trip/vehicle with a concrete date, overriding the
# generic SLA estimate the customer would otherwise see.
@router.patch("/light/{light_delivery_id}/assign")
async def assign_light_delivery(light_delivery_id: int, request: AssignLightDeliveryRequest, x_actor_user_id: int = Header(...)):
    await backend_client.patch(
        f"/api/deliveries/light/{light_delivery_id}/assign",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
    )
    return {"status": "assigned"}


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


@router.get("/bulk")
async def list_bulk_deliveries(trip_id: int | None = None):
    params = {"tripId": trip_id} if trip_id is not None else None
    return await backend_client.get("/api/deliveries/bulk", params=params)


@router.get("/bulk/{bulk_delivery_id}")
async def get_bulk_delivery(bulk_delivery_id: int):
    return await backend_client.get(f"/api/deliveries/bulk/{bulk_delivery_id}")


@router.delete("/bulk/{bulk_delivery_id}")
async def delete_bulk_order(bulk_delivery_id: int, x_actor_user_id: int = Header(...)):
    await backend_client.delete(
        f"/api/deliveries/bulk/{bulk_delivery_id}", headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )
    return {"status": "deleted"}


# The photo is fetched separately from the entry list (it's large).
@router.get("/bulk/production/{description_id}/photo")
async def bulk_production_photo(description_id: int):
    return await backend_client.get(f"/api/deliveries/bulk/production/{description_id}/photo")


@router.get("/bulk/{bulk_delivery_id}/production")
async def bulk_production_descriptions(bulk_delivery_id: int):
    return await backend_client.get(f"/api/deliveries/bulk/{bulk_delivery_id}/production")


# "add a BULK_TRIP_PRODUCTION_DESCRIPTION section to add data concerning
# that BULK_DELIVERY" - a standalone add, separate from the finalize step.
@router.post("/bulk/{bulk_delivery_id}/production")
async def create_bulk_production_description(bulk_delivery_id: int, request: ProductionDescriptionRequest):
    return await backend_client.post(
        f"/api/deliveries/bulk/{bulk_delivery_id}/production", json=request.model_dump()
    )


# "the ai will pick a picture ... and it will scan that picture for data
# and fill them" - extracts values only (a longer timeout since a vision
# model call is slower than a normal CRUD request); the admin still
# reviews/edits and saves through the endpoint above like any other entry.
@router.patch("/bulk/production/{description_id}")
async def update_bulk_production_description(description_id: int, request: ProductionDescriptionRequest):
    return await backend_client.patch(
        f"/api/deliveries/bulk/production/{description_id}", json=request.model_dump()
    )


@router.delete("/bulk/production/{description_id}")
async def delete_bulk_production_description(description_id: int, x_actor_user_id: int = Header(...)):
    return await backend_client.delete(
        f"/api/deliveries/bulk/production/{description_id}", headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )


# The route that was missing entirely: nothing on desktop could reach
# finalizeBulkDelivery (the one call that actually sets Amount_Paid, which
# is what the trip's profit is computed from - Product_Total on a line
# item never feeds it). "provide a place to allow one to edit" - re-postable,
# see FinalizeBulkDeliveryRequest.
@router.patch("/bulk/{bulk_delivery_id}/finalize")
async def finalize_bulk_delivery(bulk_delivery_id: int, request: FinalizeBulkDeliveryRequest, x_actor_user_id: int = Header(...)):
    return await backend_client.patch(
        f"/api/deliveries/bulk/{bulk_delivery_id}/finalize", json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )


@router.post("/bulk/{bulk_delivery_id}/production/scan")
async def scan_bulk_production_description(bulk_delivery_id: int, request: ScanImageRequest):
    return await backend_client.post(
        f"/api/deliveries/bulk/{bulk_delivery_id}/production/scan", json=request.model_dump(), timeout=45.0
    )
