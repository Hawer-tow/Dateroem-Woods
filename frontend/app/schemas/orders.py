from pydantic import BaseModel


class OrderItem(BaseModel):
    product_id: int
    qty: int


class CheckoutRequest(BaseModel):
    # user_id is intentionally absent: the backend derives it from the
    # caller's Authorization token, never from the request body.
    # hotspot_city is likewise gone - delivery_area_id is a real FK into
    # DELIVERY_AREAS (a dropdown the customer picks and confirms), and the
    # backend derives the city from that record instead of trusting free text.
    delivery_area_id: int
    items: list[OrderItem]
    # Optional - the M-Pesa number to send the sandbox STK push to.
    # Checkout still works without one, just skips the payment prompt.
    phone_number: str | None = None
