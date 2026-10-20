from fastapi import APIRouter

from app.core import backend_client
from app.schemas.admin import FarmerRequest, RemainderTreeRequest, TreeRequest

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
# the Insights feature.
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


# "desktop...to see the trees all associated based on a specific trip" -
# tripId scopes the list for the trip detail view; omitted (Products'
# source-tree picker, which isn't trip-scoped) means every undeleted tree,
# mirroring the Java backend's own optional param.
@router.get("/trees")
async def list_trees(tripId: int | None = None):
    params = {"tripId": tripId} if tripId is not None else None
    return await backend_client.get("/api/trees", params=params)


@router.post("/trees")
async def create_tree(request: TreeRequest):
    return await backend_client.post("/api/trees", json=request.model_dump())


@router.patch("/trees/{trees_id}")
async def update_tree(trees_id: int, request: TreeRequest):
    return await backend_client.patch(f"/api/trees/{trees_id}", json=request.model_dump())
