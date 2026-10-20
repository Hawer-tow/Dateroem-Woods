from fastapi import APIRouter

from app.core import backend_client

router = APIRouter(prefix="/shipping", tags=["shipping"])


@router.get("/areas")
async def list_areas():
    """Shipping and deliveries page: predefined pickup areas in specific
    cities where goods are delivered for customer pickup."""
    return await backend_client.get("/api/shipping/areas")
