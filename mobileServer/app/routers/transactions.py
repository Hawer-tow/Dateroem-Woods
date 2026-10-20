from fastapi import APIRouter, Header

from app.core import backend_client
from app.schemas.mobile import (
    AssignTransactionRequest,
    ConfirmSendMoneyRequest,
    ConfirmStkPushRequest,
    CreateFarmerPaymentRequest,
    SendMoneyRequest,
    StkPushRequest,
)

router = APIRouter(prefix="/transactions", tags=["transactions"])


@router.get("/unassigned")
async def unassigned():
    """The AI/Insights section's "any unassigned transactions?" view - a
    plain read-only browse, distinct from claim()/claimed() below (which
    mutate Claimed_By for the staff work-queue flow). Admin/SuperAdmin
    only, same as the rest of this router."""
    return await backend_client.get("/api/transactions/unassigned")


@router.post("/claim")
async def claim(x_actor_user_id: int = Header(...), batch_size: int = 7):
    """Claims a batch of unassigned FINANCIAL_TRANSACTION rows for this
    admin (server-side concurrency guard splits the pool across whoever's
    online, per business rules)."""
    return await backend_client.post(
        "/api/transactions/claim",
        json={},
        params={"batchSize": batch_size},
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
    )


@router.get("/claimed")
async def claimed(x_actor_user_id: int = Header(...)):
    """This admin's current working set - what they claimed and haven't
    resolved yet."""
    return await backend_client.get("/api/transactions/claimed", headers={"X-Actor-User-Id": str(x_actor_user_id)})


@router.post("/{financial_transaction_id}/release")
async def release(financial_transaction_id: int):
    """Puts a claimed-but-not-yet-assigned transaction back into the pool
    (e.g. the admin backed out of the assign screen)."""
    await backend_client.post(f"/api/transactions/{financial_transaction_id}/release", json={})
    return {"status": "released"}


@router.get("/{financial_transaction_id}/candidates")
async def candidates(financial_transaction_id: int):
    """The assign screen's suggestion list - business rules: "the system
    checks for a record of 20 ksh in the holders and provides a list if
    they are many and also checks when they were recorded and the one that
    almost matches"."""
    return await backend_client.get(f"/api/transactions/{financial_transaction_id}/candidates")


@router.post("/{financial_transaction_id}/assign")
async def assign(financial_transaction_id: int, request: AssignTransactionRequest, x_actor_user_id: int = Header(...)):
    """High-privilege action (admin): assigns a claimed FINANCIAL_TRANSACTION
    row to a specific expense/farmer-payment row - "viewing payment cost
    from other users e.g. a filled data on transport cost and assigning it"."""
    return await backend_client.post(
        f"/api/transactions/{financial_transaction_id}/assign",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
    )


@router.post("/farmer-payments")
async def send_to_farmer(request: CreateFarmerPaymentRequest, x_actor_user_id: int = Header(...)):
    """"sending money to a farmer" - the simplified payment-initiation
    flow described on the mobile transactions category page."""
    return await backend_client.post(
        "/api/farmer-payments", json=request.model_dump(), headers={"X-Actor-User-Id": str(x_actor_user_id)}, timeout=75.0
    )


@router.post("/stk-push")
async def stk_push(request: StkPushRequest, x_actor_user_id: int = Header(...)):
    """"the transaction section also have the stk push section and this
    should also work for any user since it is mostly for bulk goods" -
    open to anyone signed in; only an Admin/SuperAdmin's target/targetRowId
    actually take effect, enforced server-side in the Java backend."""
    return await backend_client.post(
        "/api/transactions/stk-push",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
    )


@router.post("/{financial_transaction_id}/stk-push/confirm")
async def confirm_stk_push(financial_transaction_id: int, request: ConfirmStkPushRequest, x_actor_user_id: int = Header(...)):
    """Stand-in for the real Safaricom Daraja callback until those
    credentials exist - whoever sent the prompt confirms it went through."""
    return await backend_client.post(
        f"/api/transactions/{financial_transaction_id}/stk-push/confirm",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
    )


@router.post("/send-money")
async def send_money(request: SendMoneyRequest, x_actor_user_id: int = Header(...)):
    """"Send Money" - pays a specific already-recorded expense holder out
    to a partner (business owner correction: expenses aren't STK, they're
    a send-money/payout). Admin-only, enforced by the Java backend."""
    return await backend_client.post(
        "/api/transactions/send-money",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
        timeout=75.0,  # the Safaricom sandbox can take 30s+ just to hand out a token
    )


@router.get("/send-money/recipient")
async def send_money_recipient(target: str, row_id: int):
    """Who (and how much) an expense entry pays out to - filled in for the
    person instead of asking them to type it."""
    return await backend_client.get("/api/transactions/send-money/recipient", params={"target": target, "rowId": row_id})


@router.get("/{financial_transaction_id}/status")
async def payment_status(financial_transaction_id: int):
    """PENDING / COMPLETED (with the M-Pesa receipt) / FAILED (with the reason) -
    polled after a payout so no receipt has to be typed in."""
    return await backend_client.get(f"/api/transactions/{financial_transaction_id}/status")


@router.post("/{financial_transaction_id}/send-money/confirm")
async def confirm_send_money(financial_transaction_id: int, request: ConfirmSendMoneyRequest, x_actor_user_id: int = Header(...)):
    """Stand-in for the real Daraja B2C result callback until those
    credentials exist."""
    return await backend_client.post(
        f"/api/transactions/{financial_transaction_id}/send-money/confirm",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
    )
