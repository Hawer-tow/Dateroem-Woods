from fastapi import APIRouter

from app.core import backend_client

router = APIRouter(prefix="/roles", tags=["roles"])


@router.get("")
async def list_roles():
    return await backend_client.get("/api/roles")
