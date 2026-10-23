from fastapi import APIRouter, Header, Query

from app.core import backend_client
from app.core.auth_header import auth_header
from app.schemas.admin import CreateUserRequest, SetUserStatusRequest, UpdateUserRequest

router = APIRouter(prefix="/users", tags=["users"])


@router.get("")
async def list_users(limit: int | None = Query(default=None), authorization: str | None = Header(default=None)):
    params = {"limit": limit} if limit is not None else None
    return await backend_client.get("/api/users", params=params, headers=auth_header(authorization))


@router.get("/{user_id}")
async def get_user(user_id: int, authorization: str | None = Header(default=None)):
    return await backend_client.get(f"/api/users/{user_id}", headers=auth_header(authorization))


@router.post("")
async def create_user(request: CreateUserRequest, authorization: str | None = Header(default=None)):
    """"add accounts" - a staff account with any role (marketer,
    products_staff, worker, manager, admin, superadmin), unlike public
    signup which always creates a Customer."""
    return await backend_client.post("/api/users", json=request.model_dump(), headers=auth_header(authorization))


@router.patch("/{user_id}")
async def update_user(user_id: int, request: UpdateUserRequest, authorization: str | None = Header(default=None)):
    body = {k: v for k, v in request.model_dump().items() if v is not None}
    return await backend_client.patch(f"/api/users/{user_id}", json=body, headers=auth_header(authorization))


# "is it proper to allow the superadmin to see actual passwords" - no one can;
# an admin resets it to a temporary password, shown once, which the person
# must replace at their next sign-in (migration 039; only the SuperAdmin
# resets an Admin - the backend checks).
@router.post("/{user_id}/reset-password")
async def reset_password(user_id: int, authorization: str | None = Header(default=None)):
    return await backend_client.post(f"/api/users/{user_id}/reset-password", json={}, headers=auth_header(authorization))


@router.patch("/{user_id}/status")
async def set_user_status(
    user_id: int, request: SetUserStatusRequest, authorization: str | None = Header(default=None)
):
    """"one can even disable a certain phone user" - flips USERS.Status,
    which the backend now actually enforces at login (previously present
    in the schema but silently ignored)."""
    return await backend_client.patch(
        f"/api/users/{user_id}/status", json=request.model_dump(), headers=auth_header(authorization)
    )
