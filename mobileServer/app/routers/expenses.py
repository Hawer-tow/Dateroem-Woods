from typing import Optional

from fastapi import APIRouter, Header

from app.core import backend_client
from app.schemas.mobile import CreateExpenseRequest

router = APIRouter(tags=["expenses"])


@router.get("/trips/{trip_id}/expenses")
async def category_totals(trip_id: int):
    """Mobile expenses categories page: totals for loan, capital,
    farmer-payments, salaries, accomodation, transport, communications,
    machine-expenses, other-expenses, permits - one number per category."""
    return await backend_client.get(f"/api/trips/{trip_id}/expenses")


@router.get("/trips/{trip_id}/expenses/{category}")
async def line_items(trip_id: int, category: str, assigned: Optional[str] = "all"):
    """Drill into one category's individual line items for this trip
    (business rules: "can see all e.g. accomodation expenses so far
    depending on the trip"). assigned=all|assigned|pending toggles between
    everything and just what's already matched to a real payment (business
    rules: "2 sections ... toggle to the all or assigned ones")."""
    return await backend_client.get(f"/api/trips/{trip_id}/expenses/{category}", params={"assigned": assigned})


@router.post("/trips/{trip_id}/expenses/{category}")
async def create_expense(trip_id: int, category: str, request: CreateExpenseRequest, x_actor_user_id: int = Header(...)):
    """A normal user creating an unvalidated holder - business rules:
    "creates a holder since this is stored in the system but not yet
    validated and then later it will be validated by admins" (via the
    transactions candidates/assign flow)."""
    return await backend_client.post(
        f"/api/trips/{trip_id}/expenses/{category}",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
    )


@router.delete("/trips/{trip_id}/expenses/{category}/{row_id}")
async def delete_expense(trip_id: int, category: str, row_id: int, x_actor_user_id: int = Header(...)):
    """A user can delete their own not-yet-assigned holder if it was
    wrong - blocked server-side once it's already matched to a real
    payment (business rules: "can delete it if it was wrong")."""
    await backend_client.delete(
        f"/api/trips/{trip_id}/expenses/{category}/{row_id}",
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
    )
    return {"status": "deleted"}
