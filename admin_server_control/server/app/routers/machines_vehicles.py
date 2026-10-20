from fastapi import APIRouter

from app.core import backend_client
from app.schemas.admin import MachineRequest, VehicleRequest

router = APIRouter(tags=["machines-vehicles"])


# "machine should be selected from the machine table" / "select the
# vehicle one used instead of using the vehicle id" - the desktop app is
# where these two get created ("adding of a new machine or vehicle is
# only allowed in the desktop"); mobile only ever lists them.
@router.get("/machines")
async def list_machines():
    return await backend_client.get("/api/machines")


@router.post("/machines")
async def create_machine(request: MachineRequest):
    return await backend_client.post("/api/machines", json=request.model_dump())


@router.get("/vehicles")
async def list_vehicles():
    return await backend_client.get("/api/vehicles")


@router.post("/vehicles")
async def create_vehicle(request: VehicleRequest):
    return await backend_client.post("/api/vehicles", json=request.model_dump())


# "for an already created object provide an edit area" - machines and
# vehicles can now be corrected after they're created.
@router.patch("/machines/{machine_id}")
async def update_machine(machine_id: int, request: MachineRequest):
    return await backend_client.patch(f"/api/machines/{machine_id}", json=request.model_dump())


@router.patch("/vehicles/{vehicle_id}")
async def update_vehicle(vehicle_id: int, request: VehicleRequest):
    return await backend_client.patch(f"/api/vehicles/{vehicle_id}", json=request.model_dump())
