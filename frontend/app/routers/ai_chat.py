from typing import Literal

from fastapi import APIRouter, Header
from pydantic import BaseModel, Field

from app.core import backend_client
from app.core.config import settings

router = APIRouter(prefix="/ai/chat", tags=["ai-chat"])


class ChatTurn(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(max_length=4000)


class CartLine(BaseModel):
    product_id: int
    quantity: int = Field(ge=1, le=100_000)


class ChatContext(BaseModel):
    """Which page the question was asked from - only ids, quantities and a
    search phrase; the backend looks the products up itself."""

    page: Literal["product", "cart", "search"]
    product_id: int | None = None
    cart: list[CartLine] = Field(default_factory=list, max_length=30)
    search: str | None = Field(default=None, max_length=80)


class AiChatMessageRequest(BaseModel):
    message: str = Field(max_length=2000)
    # A guest's own recent turns, kept only in their browser - the backend
    # has no stored conversation for someone without an account, so this is
    # what lets "and how much is it?" refer back to the previous answer.
    history: list[ChatTurn] = Field(default_factory=list, max_length=12)
    # "the chat can already know which product or cart the visitor is
    # looking at" - so "is this in stock?" needs no explaining.
    context: ChatContext | None = None


# The website's generative assistant - same backend feature as the mobile
# app's AI section, just with channel="web" so AiChatService picks the
# website's own page list (products/cart/shipping/help/etc.) instead of
# the mobile app's screens when it suggests a shortcut.
#
# "the ai should work even if you dont have an account but if you are
# logged in it will provide dedicated answers based on your account" -
# signing in is no longer required: an anonymous visitor's headers are
# simply omitted, and the backend answers without conversation history or
# account context. A signed-in visitor's headers are forwarded as before,
# which is what unlocks the backend's own-order lookups (the backend checks
# the token really belongs to that account).
@router.post("")
async def send_message(
    request: AiChatMessageRequest,
    authorization: str | None = Header(default=None),
    x_actor_user_id: int | None = Header(default=None),
):
    headers = {}
    signed_in = bool(authorization) and x_actor_user_id is not None
    if signed_in:
        headers = {"Authorization": authorization, "X-Actor-User-Id": str(x_actor_user_id)}
    body = {
        "message": request.message,
        "channel": "web",
        # The same public contact details the Help page shows - the
        # assistant quotes these instead of promising a callback.
        "siteInfo": {
            "phone": settings.support_phone,
            "whatsapp": settings.support_whatsapp,
            "email": settings.support_email,
        },
    }
    if not signed_in and request.history:
        body["history"] = [turn.model_dump() for turn in request.history]
    if request.context:
        body["context"] = {
            "page": request.context.page,
            "productId": request.context.product_id,
            "cart": [{"productId": line.product_id, "quantity": line.quantity} for line in request.context.cart],
            "search": request.context.search,
        }
    return await backend_client.post("/api/ai/chat", json=body, headers=headers, timeout=45.0)


@router.get("/status")
async def status():
    """{"available": bool} - whether the chat button may invite questions
    right now (the assistant is set up and not at its per-minute limit)."""
    try:
        return await backend_client.get("/api/ai/chat/status")
    except Exception:  # backend down - certainly don't invite questions
        return {"available": False}


@router.get("/history")
async def history(
    authorization: str | None = Header(default=None),
    x_actor_user_id: int | None = Header(default=None),
):
    if not authorization or x_actor_user_id is None:
        return []
    return await backend_client.get(
        "/api/ai/chat/history", headers={"Authorization": authorization, "X-Actor-User-Id": str(x_actor_user_id)}
    )
