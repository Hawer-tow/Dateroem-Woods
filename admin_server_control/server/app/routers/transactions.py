from fastapi import APIRouter, Header

from app.core import backend_client
from app.schemas.admin import AssignTransactionRequest, ConfirmStkPushRequest, SendMoneyRequest, StkPushRequest

router = APIRouter(prefix="/transactions", tags=["transactions"])


# "i think its also fine time we add stk push in the desktop" - same call
# the mobile app's own StkPushScreen makes: prompts a customer's phone for
# an M-Pesa payment (mainly for bulk-goods payments taken over the desk).
# Open to any signed-in actor, no pre-targeting - enforced server-side.
@router.post("/stk-push")
async def stk_push(request: StkPushRequest, x_actor_user_id: int = Header(...)):
    return await backend_client.post(
        "/api/transactions/stk-push",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
    )


# Stand-in for the real Daraja STK callback until those credentials exist -
# whoever sent the prompt confirms it went through with the receipt code.
@router.post("/{financial_transaction_id}/stk-push/confirm")
async def confirm_stk_push(financial_transaction_id: int, request: ConfirmStkPushRequest, x_actor_user_id: int = Header(...)):
    return await backend_client.post(
        f"/api/transactions/{financial_transaction_id}/stk-push/confirm",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
    )


# "you ask any unassigned transactions? it will provide them and when you
# click them it will lead you to being taken to the assign transaction" -
# the first insights-layer feature: a plain read-only list (the backend
# already had claim/claimed for the staff work-queue flow, but nothing
# for just looking).
@router.get("/unassigned")
async def unassigned():
    return await backend_client.get("/api/transactions/unassigned")


# Who (and how much) an expense entry or loan pays out to, worked out from the
# entry itself - so Send Money / Pay Loan come with the fields filled in.
# Pays an expense entry out from the company wallet (M-Pesa B2C) - the same
# call the mobile app makes; admin-only, enforced by the backend.
@router.post("/send-money")
async def send_money(request: SendMoneyRequest, x_actor_user_id: int = Header(...)):
    return await backend_client.post(
        "/api/transactions/send-money",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
        timeout=75.0,  # the Safaricom sandbox can take 30s+ just to hand out a token
    )


@router.get("/send-money/recipient")
async def send_money_recipient(target: str, row_id: int):
    return await backend_client.get("/api/transactions/send-money/recipient", params={"target": target, "rowId": row_id})


# PENDING / COMPLETED (with the M-Pesa receipt) / FAILED (with the reason) -
# watched after a payout so the receipt never has to be typed in.
@router.get("/{financial_transaction_id}/status")
async def payment_status(financial_transaction_id: int):
    return await backend_client.get(f"/api/transactions/{financial_transaction_id}/status")


@router.get("/{financial_transaction_id}/candidates")
async def candidates(financial_transaction_id: int):
    return await backend_client.get(f"/api/transactions/{financial_transaction_id}/candidates")


@router.post("/{financial_transaction_id}/assign")
async def assign(financial_transaction_id: int, request: AssignTransactionRequest, x_actor_user_id: int = Header(...)):
    await backend_client.post(
        f"/api/transactions/{financial_transaction_id}/assign",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
    )
    return {"status": "assigned"}
