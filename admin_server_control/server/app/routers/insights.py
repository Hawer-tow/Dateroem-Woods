from fastapi import APIRouter

from app.core import backend_client

router = APIRouter(prefix="/insights", tags=["insights"])


# "for the expense it can scan for the expense provide information on
# the most expensive expense and observe even from other trips" - part
# of the Insights feature; mirrors the Java backend's own new
# InsightsController.
@router.get("/expenses/breakdown")
async def expense_breakdown():
    return await backend_client.get("/api/insights/expenses/breakdown")
