from fastapi import APIRouter

from app.core import backend_client
from app.schemas.admin import ShippingAreaRequest

router = APIRouter(prefix="/shipping", tags=["shipping"])


@router.get("/areas")
async def list_areas(include_inactive: bool = True):
    return await backend_client.get("/api/shipping/areas", params={"includeInactive": include_inactive})


@router.get("/areas/{area_id}")
async def get_area(area_id: int):
    return await backend_client.get(f"/api/shipping/areas/{area_id}")


@router.post("/areas")
async def create_area(request: ShippingAreaRequest):
    return await backend_client.post("/api/shipping/areas", json=request.model_dump())


@router.patch("/areas/{area_id}")
async def update_area(area_id: int, request: ShippingAreaRequest):
    return await backend_client.patch(f"/api/shipping/areas/{area_id}", json=request.model_dump())
