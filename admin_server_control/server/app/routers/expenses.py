from typing import Optional

from fastapi import APIRouter, Header

from app.core import backend_client
from app.schemas.admin import CreateExpenseRequest

router = APIRouter(prefix="/trips/{trip_id}/expenses", tags=["expenses"])


# "in the trip add a tab expenses ... add all the expenses as tabs" - the
# desktop's per-trip Expenses tab: totals per category, then one category's
# line items (assigned = all | assigned | pending), creating an entry, and
# deleting one that hasn't been paid/assigned.
@router.get("")
async def category_totals(trip_id: int):
    return await backend_client.get(f"/api/trips/{trip_id}/expenses")


@router.get("/{category}")
async def line_items(trip_id: int, category: str, assigned: Optional[str] = "all"):
    return await backend_client.get(f"/api/trips/{trip_id}/expenses/{category}", params={"assigned": assigned})


@router.post("/{category}")
async def create_expense(trip_id: int, category: str, request: CreateExpenseRequest, x_actor_user_id: int = Header(...)):
    return await backend_client.post(
        f"/api/trips/{trip_id}/expenses/{category}",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
    )


@router.delete("/{category}/{row_id}")
async def delete_expense(trip_id: int, category: str, row_id: int, x_actor_user_id: int = Header(...)):
    await backend_client.delete(
        f"/api/trips/{trip_id}/expenses/{category}/{row_id}", headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )
    return {"status": "deleted"}
