from fastapi import APIRouter

from app.core import backend_client

router = APIRouter(tags=["machines-vehicles"])


# "machine should be selected from the machine table" / "select the
# vehicle one used instead of using the vehicle id" - list-only here.
# "adding of a new machine or vehicle is only allowed in the desktop" -
# no create/update proxied to the mobile app on purpose.
@router.get("/machines")
async def list_machines():
    return await backend_client.get("/api/machines")


@router.get("/vehicles")
async def list_vehicles():
    return await backend_client.get("/api/vehicles")
