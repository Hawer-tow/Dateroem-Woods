from fastapi import APIRouter, Query

from app.core import backend_client

router = APIRouter(prefix="/computers", tags=["computers"])


@router.get("")
async def list_computers(status: str | None = Query(default=None)):
    """Every computer that has signed in to the desktop app, waiting ones
    first. status: PENDING, ACTIVE or REVOKED (omit for all). Admins see the
    list; only the SuperAdmin approves or blocks (checked by the backend)."""
    params = {"status": status} if status else None
    return await backend_client.get("/api/computers", params=params)


@router.post("/{computer_id}/approve")
async def approve_computer(computer_id: int, replace: bool = Query(default=False)):
    """Approve a waiting computer. replace=true also blocks the account's
    current computer in the same step (one approved computer per account)."""
    return await backend_client.post(
        f"/api/computers/{computer_id}/approve", json={}, params={"replace": str(replace).lower()}
    )


@router.post("/{computer_id}/revoke")
async def revoke_computer(computer_id: int):
    """Block a computer - a waiting one is rejected, an approved one is
    signed out at once."""
    return await backend_client.post(f"/api/computers/{computer_id}/revoke", json={})
