from fastapi import APIRouter, Header

from app.core import backend_client
from app.schemas.mobile import FarmerRequest, RemainderTreeRequest, TreeRequest

router = APIRouter(tags=["trees"])


@router.get("/farmers")
async def list_farmers():
    return await backend_client.get("/api/farmers")


@router.post("/farmers")
async def create_farmer(request: FarmerRequest):
    return await backend_client.post("/api/farmers", json=request.model_dump())


@router.patch("/farmers/{farmer_id}")
async def update_farmer(farmer_id: int, request: FarmerRequest):
    return await backend_client.patch(f"/api/farmers/{farmer_id}", json=request.model_dump())


# "check a certain farmer and provide info that this farmer tops on the
# list as one of the farmers whom you bought most trees from" - part of
# the AI/Insights section, admin/SuperAdmin only.
@router.get("/farmers/insights/top-suppliers")
async def farmer_top_suppliers():
    return await backend_client.get("/api/farmers/insights/top-suppliers")


@router.get("/remainder-trees")
async def list_remainder_trees(previousTripId: int | None = None):
    params = {"previousTripId": previousTripId} if previousTripId is not None else None
    return await backend_client.get("/api/remainder-trees", params=params)


@router.post("/remainder-trees")
async def create_remainder_tree(request: RemainderTreeRequest):
    return await backend_client.post("/api/remainder-trees", json=request.model_dump())


@router.patch("/remainder-trees/{remainder_trees_id}")
async def update_remainder_tree(remainder_trees_id: int, request: RemainderTreeRequest):
    return await backend_client.patch(f"/api/remainder-trees/{remainder_trees_id}", json=request.model_dump())


# tripId required - "select from trees already registered for a certain
# trip" is the only way this list is ever used on mobile.
@router.get("/trees")
async def list_trees(tripId: int):
    return await backend_client.get("/api/trees", params={"tripId": tripId})


@router.post("/trees")
async def create_tree(request: TreeRequest):
    return await backend_client.post("/api/trees", json=request.model_dump())


@router.patch("/trees/{trees_id}")
async def update_tree(trees_id: int, request: TreeRequest):
    return await backend_client.patch(f"/api/trees/{trees_id}", json=request.model_dump())


# "delete trees should also be created and add a bin just like the
# product section does it" - same soft-delete/restore scheme as products.
@router.get("/trees/deleted")
async def list_deleted_trees():
    return await backend_client.get("/api/trees/deleted")


@router.delete("/trees/{trees_id}")
async def delete_tree(trees_id: int, x_actor_user_id: int = Header(...)):
    await backend_client.delete(f"/api/trees/{trees_id}", headers={"X-Actor-User-Id": str(x_actor_user_id)})
    return {"status": "deleted"}


@router.post("/trees/{trees_id}/restore")
async def restore_tree(trees_id: int, x_actor_user_id: int = Header(...)):
    return await backend_client.post(
        f"/api/trees/{trees_id}/restore", json={}, headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )
