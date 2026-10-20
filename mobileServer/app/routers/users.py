from fastapi import APIRouter, Header

from app.core import backend_client

router = APIRouter(prefix="/users", tags=["users"])


# "updating the number in the payment ... should also show the change
# [in] the name" - resolves a typed phone number to whoever it belongs
# to, for the payout confirmation screen. Enforced by the Java backend's
# own real JWT check (any signed-in user, not admin-only) - the
# Authorization header is forwarded through untouched, same reasoning as
# orders.py's drop endpoint.
@router.get("/lookup-by-phone")
async def lookup_by_phone(phone: str, authorization: str | None = Header(default=None)):
    headers = {"Authorization": authorization} if authorization else {}
    return await backend_client.get("/api/users/lookup-by-phone", params={"phone": phone}, headers=headers)


# "connect major expense to a user not just who created it ... who its
# meant for" - the mobile "For User" picker's source list.
@router.get("/picker")
async def list_for_picker(authorization: str | None = Header(default=None)):
    headers = {"Authorization": authorization} if authorization else {}
    return await backend_client.get("/api/users/picker", headers=headers)
