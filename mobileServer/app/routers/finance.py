from fastapi import APIRouter, Header

from app.core import backend_client
from app.schemas.mobile import LoanRequest, PayLoanRequest, RepaymentRequest, UpdateLoanRequest

router = APIRouter(tags=["finance"])


@router.get("/trips/{trip_id}/finance/loans")
async def loans(trip_id: int):
    """Loans and finance page - admin/SuperAdmin only (role enforcement
    pending real auth)."""
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


@router.get("/loans/{loan_id}/repayments")
async def repayments(loan_id: int):
    return await backend_client.get(f"/api/loans/{loan_id}/repayments")


@router.post("/loans/{loan_id}/repayments")
async def record_repayment(loan_id: int, request: RepaymentRequest):
    return await backend_client.post(f"/api/loans/{loan_id}/repayments", json=request.model_dump())


@router.post("/loans/{loan_id}/repayments/pay")
async def pay_loan(loan_id: int, request: PayLoanRequest, x_actor_user_id: int = Header(...)):
    """"an option pay loan in it if the loan repayments have not yet
    reached the expected amount to clear loan ... led to the transactions
    area and the option loan repayment for the selected loan" - admin-only,
    enforced by the Java backend."""
    return await backend_client.post(
        f"/api/loans/{loan_id}/repayments/pay",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
        timeout=75.0,  # the Safaricom sandbox can take 30s+ just to hand out a token
    )
