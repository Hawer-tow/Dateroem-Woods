from fastapi import APIRouter, Header, HTTPException

from app.core import backend_client
from app.schemas.orders import CheckoutRequest

router = APIRouter(prefix="/orders", tags=["orders"])


@router.post("/checkout")
async def checkout(request: CheckoutRequest, authorization: str | None = Header(default=None)):
    """Light product order page: the cart itself lives client-side (React) -
    this endpoint turns the chosen items into a real ORDERS/ORDER_ITEMS
    record via the backend and hands back an order id for the
    shipping/delivery page to track. The backend also opens a matching
    LIGHT_DELIVERIES row right away, so there's a real delivery record to
    check from the moment the order is placed.

    Checkout requires being signed in: the backend derives the order's
    User_ID from this Authorization token, never from the request body, so
    browsing and cart-building stay open to everyone but paying requires an
    account.

    "place the stkpush directly since its in the sandbox" - a phone number
    triggers a real Daraja sandbox STK push for the order's total; the
    response's stkPushSent tells the website whether to show "check your
    phone", and stkPushError carries Safaricom's own reason when it
    couldn't be sent (the order itself still gets created either way)."""
    if not authorization:
        raise HTTPException(status_code=401, detail="Sign in to place an order")
    payload = {
        "deliveryAreaId": request.delivery_area_id,
        "items": [{"productId": item.product_id, "qty": item.qty} for item in request.items],
        "phoneNumber": request.phone_number,
    }
    return await backend_client.post("/api/orders", json=payload, headers={"Authorization": authorization})


@router.get("/mine")
async def my_orders(authorization: str | None = Header(default=None), limit: int = 5):
    """Backs the help chat's "where's my order" lookup and any future
    order-history view - most recent first."""
    if not authorization:
        raise HTTPException(status_code=401, detail="Sign in required")
    return await backend_client.get("/api/orders/mine", params={"limit": limit}, headers={"Authorization": authorization})


@router.get("/track")
async def track_order(ticket: str = "", phone: str = ""):
    """Track my order - no sign-in (23 October 2026): the order's ticket
    (DW-7K3Q9P) and the last 3 digits of the account's phone number. The
    backend checks both and limits wrong tries; the answer carries no name
    or phone number. Declared before /{order_id} so "track" isn't read as
    an order id."""
    return await backend_client.get("/api/orders/track", params={"ticket": ticket[:20], "phone": phone[:6]})


@router.get("/{order_id}")
async def get_order(order_id: int, authorization: str | None = Header(default=None)):
    """Order confirmation/status page - ownership-checked on the backend."""
    if not authorization:
        raise HTTPException(status_code=401, detail="Sign in required")
    return await backend_client.get(f"/api/orders/{order_id}", headers={"Authorization": authorization})
