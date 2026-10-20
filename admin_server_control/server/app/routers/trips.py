from fastapi import APIRouter, Header

from app.core import backend_client
from app.schemas.admin import CreateTripRequest, FinalizeTripRequest, UpdateTripRequest

router = APIRouter(prefix="/trips", tags=["trips"])


@router.get("")
async def list_trips(limit: int | None = None):
    # "desktop one should see all the trips but the mobile app only loads
    # the last 6" - unlimited by default; the mobile BFF is where 6
    # belongs. The Java backend's own /api/trips used to silently default
    # to 6 regardless of what this passed through - that's fixed now too
    # (limit=None means "all", matching Products/Campaigns/Users).
    params = {"limit": limit} if limit is not None else None
    return await backend_client.get("/api/trips", params=params)


# "an area to allow one to create a new trip not just selecting the
# already preexisting trips" - the backend has always supported this
# (mobile creates trips this way already); the desktop BFF just never
# proxied it.
@router.post("")
async def create_trip(request: CreateTripRequest):
    return await backend_client.post("/api/trips", json=request.model_dump())


# "you can never create a new trip before you finalize every detail in the
# other trip" - the open (not yet finalized) trip, if any.
@router.get("/open")
async def open_trip():
    return await backend_client.get("/api/trips/open")


@router.get("/{trip_id}/finalization")
async def finalization_check(trip_id: int):
    return await backend_client.get(f"/api/trips/{trip_id}/finalization")


@router.post("/{trip_id}/finalize")
async def finalize_trip(trip_id: int, request: FinalizeTripRequest, x_actor_user_id: int = Header(...)):
    return await backend_client.post(
        f"/api/trips/{trip_id}/finalize", json=request.model_dump(), headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )


@router.get("/{trip_id}")
async def get_trip(trip_id: int):
    return await backend_client.get(f"/api/trips/{trip_id}")


# "in trip allow editing since an admin user can even edit this" - the
# backend already supports this (mobile's had it all along); the desktop
# BFF just never proxied it.
@router.patch("/{trip_id}")
async def update_trip(trip_id: int, request: UpdateTripRequest):
    await backend_client.patch(f"/api/trips/{trip_id}", json=request.model_dump())
    return {"status": "updated"}


# "a trip can be deleted and this will force all the assigned orders back
# to the unassigned ones" - undelivered orders return to the pending list
# without priority; see TripService.delete on the backend.
@router.delete("/{trip_id}")
async def delete_trip(trip_id: int, x_actor_user_id: int = Header(...)):
    await backend_client.delete(f"/api/trips/{trip_id}", headers={"X-Actor-User-Id": str(x_actor_user_id)})
    return {"status": "deleted"}


@router.get("/{trip_id}/summary")
async def trip_summary(trip_id: int):
    return await backend_client.get(f"/api/trips/{trip_id}/summary")


@router.get("/{trip_id}/deliveries")
async def trip_deliveries(trip_id: int):
    light = await backend_client.get("/api/deliveries/light", params={"tripId": trip_id})
    bulk = await backend_client.get("/api/deliveries/bulk", params={"tripId": trip_id})
    return {"light": light, "bulk": bulk, "lightCount": len(light), "bulkCount": len(bulk)}
