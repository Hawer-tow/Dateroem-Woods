from fastapi import APIRouter, Header, Query

from app.core import backend_client
from app.core.auth_header import auth_header

router = APIRouter(prefix="/devices", tags=["devices"])


@router.get("")
async def list_devices(status: str | None = Query(default=None), authorization: str | None = Header(default=None)):
    """"allows only one user per account per mobile phone" - every phone
    that has signed in, waiting ones first. status: PENDING, ACTIVE or
    REVOKED (omit for all). Admins only (checked by the backend)."""
    params = {"status": status} if status else None
    return await backend_client.get("/api/devices", params=params, headers=auth_header(authorization))


@router.post("/{device_id}/approve")
async def approve_device(
    device_id: int, replace: bool = Query(default=False), authorization: str | None = Header(default=None)
):
    """Approve a waiting phone. replace=true also revokes the account's
    current phone in the same step (one approved phone per account)."""
    return await backend_client.post(
        f"/api/devices/{device_id}/approve", json={}, params={"replace": str(replace).lower()}, headers=auth_header(authorization)
    )


@router.post("/{device_id}/revoke")
async def revoke_device(device_id: int, authorization: str | None = Header(default=None)):
    """Block a phone - a waiting one is rejected, an approved one is signed
    out at once."""
    return await backend_client.post(f"/api/devices/{device_id}/revoke", json={}, headers=auth_header(authorization))
