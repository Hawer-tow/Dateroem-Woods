from fastapi import APIRouter, Header

from app.core import backend_client
from app.schemas.admin import CapitalRequest, CashSplitRequest, LoanRequest, PayLoanRequest, RepaymentRequest, UpdateLoanRequest

router = APIRouter(tags=["finance"])


@router.get("/trips/{trip_id}/finance/loans")
async def loans(trip_id: int):
    return await backend_client.get(f"/api/trips/{trip_id}/finance/loans")


@router.post("/trips/{trip_id}/finance/loans")
async def create_loan(trip_id: int, request: LoanRequest, x_actor_user_id: int = Header(...)):
    """"allow one to access loans ... or even edit and add a new loan" -
    admin-only, enforced by the Java backend."""
    return await backend_client.post(
        f"/api/trips/{trip_id}/finance/loans",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
    )


@router.patch("/loans/{loan_id}")
async def update_loan(loan_id: int, request: UpdateLoanRequest, x_actor_user_id: int = Header(...)):
    await backend_client.patch(
        f"/api/loans/{loan_id}",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
    )
    return {"status": "updated"}


@router.get("/trips/{trip_id}/finance/capital")
async def capital(trip_id: int):
    return await backend_client.get(f"/api/trips/{trip_id}/finance/capital")


@router.post("/trips/{trip_id}/finance/capital")
async def create_capital(trip_id: int, request: CapitalRequest, x_actor_user_id: int = Header(...)):
    """"add the finances one had at the start of the trip" - admin-only,
    enforced by the Java backend."""
    return await backend_client.post(
        f"/api/trips/{trip_id}/finance/capital",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
    )


@router.patch("/finance/capital/{capital_id}")
async def update_capital(capital_id: int, request: CapitalRequest, x_actor_user_id: int = Header(...)):
    """Edit an existing capital entry - admin-only, enforced by the backend."""
    await backend_client.patch(
        f"/api/finance/capital/{capital_id}",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
    )
    return {"status": "updated"}


@router.put("/trips/{trip_id}/finance/cash-split")
async def set_cash_split(trip_id: int, request: CashSplitRequest):
    await backend_client.put(f"/api/trips/{trip_id}/cash-split", json=request.model_dump())
    return {"status": "updated"}


@router.get("/loans/{loan_id}/repayments")
async def repayments(loan_id: int):
    return await backend_client.get(f"/api/loans/{loan_id}/repayments")


@router.post("/loans/{loan_id}/repayments")
async def record_repayment(loan_id: int, request: RepaymentRequest):
    return await backend_client.post(f"/api/loans/{loan_id}/repayments", json=request.model_dump())


@router.post("/loans/{loan_id}/repayments/pay")
async def pay_loan(loan_id: int, request: PayLoanRequest, x_actor_user_id: int = Header(...)):
    """Real Daraja B2C payout, same "Send Money" pathway the mobile app
    uses - admin-only, enforced by the Java backend."""
    return await backend_client.post(
        f"/api/loans/{loan_id}/repayments/pay",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
        timeout=75.0,  # the Safaricom sandbox can take 30s+ just to hand out a token
    )
