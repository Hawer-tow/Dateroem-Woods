from fastapi import APIRouter

from app.core import backend_client

router = APIRouter(prefix="/insights", tags=["insights"])


# "for the expense it can scan for the expense provide information on
# the most expensive expense and observe even from other trips" - part
# of the AI/Insights section, admin/SuperAdmin only. Mirrors the desktop
# admin server's own insights router - same backend endpoint either way.
@router.get("/expenses/breakdown")
async def expense_breakdown():
    return await backend_client.get("/api/insights/expenses/breakdown")


# The business at a glance on Home for Admins and the SuperAdmin (24 October
# 2026): users, products, campaigns, trips and the orders waiting - the same
# figures as the desktop dashboard, in one answer.
@router.get("/overview")
async def overview():
    return await backend_client.get("/api/insights/overview")
