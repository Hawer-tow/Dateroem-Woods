import asyncio

from fastapi import APIRouter, Header

from app.core import backend_client
from app.schemas.mobile import CashSplitRequest, CreateTripRequest, UpdateTripRequest

router = APIRouter(prefix="/trips", tags=["trips"])

_in_flight = 0
_lock = asyncio.Lock()


def _batch_size_for_load(concurrent: int) -> int:
    """Load heuristic (business rules: "send only a maximum of data related
    to 3-6 trips ... if its loaded even 2 or even 1 trip data can be sent
    to reduce load in traffic times"). This is a simple proxy - how many
    /trips requests this mobileServer instance is handling right now - not
    a full metrics/APM-based load balancer."""
    if concurrent <= 1:
        return 6
    if concurrent <= 3:
        return 4
    if concurrent <= 6:
        return 2
    return 1


@router.get("")
async def list_trips():
    global _in_flight
    async with _lock:
        _in_flight += 1
        current_load = _in_flight
    try:
        limit = _batch_size_for_load(current_load)
        return await backend_client.get("/api/trips", params={"limit": limit})
    finally:
        async with _lock:
            _in_flight -= 1


@router.get("/open")
async def open_trip():
    """The one trip not yet finalized (null when none) - a new trip can't be
    started until it is."""
    return await backend_client.get("/api/trips/open")


@router.get("/{trip_id}/finalization")
async def finalization_check(trip_id: int):
    return await backend_client.get(f"/api/trips/{trip_id}/finalization")


@router.post("/{trip_id}/finalize")
async def finalize_trip(trip_id: int, request: dict, x_actor_user_id: int = Header(...)):
    return await backend_client.post(
        f"/api/trips/{trip_id}/finalize", json={"dropUndelivered": bool(request.get("dropUndelivered", False))},
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
    )


@router.get("/{trip_id}")
async def get_trip(trip_id: int):
    return await backend_client.get(f"/api/trips/{trip_id}")


@router.get("/{trip_id}/summary")
async def trip_summary(trip_id: int):
    """Trip page: profit + expense category totals - the heavily-cached
    part (real caching still TODO, see backend_client). Delivery counts
    and loan detail are fetched separately, per the "rest done one by one"
    caching philosophy."""
    return await backend_client.get(f"/api/trips/{trip_id}/summary")


@router.get("/{trip_id}/deliveries")
async def trip_deliveries(trip_id: int):
    """business rules: "see things like how many light and heavy
    deliveries associated to it"."""
    light = await backend_client.get("/api/deliveries/light", params={"tripId": trip_id})
    bulk = await backend_client.get("/api/deliveries/bulk", params={"tripId": trip_id})
    return {"light": light, "bulk": bulk, "lightCount": len(light), "bulkCount": len(bulk)}


@router.post("")
async def create_trip(request: CreateTripRequest):
    """"this is the section where i want one to create a trip so that
    then the rest of the sections are active since almost all are
    connected to the trip factor"."""
    return await backend_client.post("/api/trips", json=request.model_dump())


@router.patch("/{trip_id}")
async def update_trip(trip_id: int, request: UpdateTripRequest):
    await backend_client.patch(f"/api/trips/{trip_id}", json=request.model_dump())
    return {"status": "updated"}


@router.post("/{trip_id}/total-payment/recalculate")
async def recalculate_total_payment(trip_id: int):
    """business rules: "how total amount paid in bulk delivery + product
    payment amount ... the total amount in trip is calculated"."""
    return await backend_client.post(f"/api/trips/{trip_id}/total-payment/recalculate", json={})


@router.put("/{trip_id}/cash-split")
async def set_cash_split(trip_id: int, request: CashSplitRequest):
    """"the hardcash part is written manually"."""
    await backend_client.put(f"/api/trips/{trip_id}/cash-split", json=request.model_dump())
    return {"status": "updated"}
