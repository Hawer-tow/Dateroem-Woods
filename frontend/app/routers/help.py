import asyncio
import logging
import smtplib

from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel

from app.core import backend_client, support_mail
from app.core.config import settings
from app.core.contacts import whatsapp_link

log = logging.getLogger(__name__)

router = APIRouter(prefix="/help", tags=["help"])

DELIVERY_KEYWORDS = (
    "deliver", "delivery", "shipping", "shipped", "order", "arrive",
    "arriving", "track", "tracking", "status", "when will", "where is",
)


class ChatRequest(BaseModel):
    question: str


class SupportEmailRequest(BaseModel):
    name: str = ""
    email: str = ""
    phone: str = ""
    subject: str = ""
    message: str = ""
    # Which of our inboxes: "support" (Help page) or "orders" (Bulk Orders).
    desk: str = "support"
    # A field people never see - only bots fill it in.
    website: str = ""
    # How long the form was open before Send, from the page.
    elapsedMs: int = 0


# Grounded lookup bot, not a generative model (business decision: no
# external AI provider/API key, so answers come straight from our own
# data instead) - "connected to a few none harmfull databases e.g
# products and if one is logged in it can anwer things like product
# delivery and when it being delivered". Two lookups only: PRODUCTS (open
# to anyone) and the caller's own ORDERS/LIGHT_DELIVERIES (signed-in only -
# never another customer's data, since the backend derives the order list
# from the caller's own JWT).
@router.post("/chat")
async def chat(request: ChatRequest, authorization: str | None = Header(default=None)):
    question = request.question.strip()
    lowered = question.lower()

    if any(keyword in lowered for keyword in DELIVERY_KEYWORDS):
        return await _answer_delivery_question(authorization)

    product_answer = await _answer_product_question(lowered)
    if product_answer:
        return product_answer

    # Only reached when the real assistant couldn't be reached at all - an
    # honest "try again / here's who to call" rather than promising a
    # callback nobody is going to make.
    return {
        "answer": (
            "I can't answer that one right now - please try again in a moment, or reach the team "
            f"directly: call or WhatsApp {settings.support_phone}, or email {settings.support_email}."
        ),
        "escalate_to": settings.support_email,
    }


async def _answer_delivery_question(authorization: str | None):
    if not authorization:
        return {
            "answer": "Sign in and I can check your order's delivery status for you - or reach us directly below.",
            "escalate_to": settings.support_email,
        }
    try:
        orders = await backend_client.get(
            "/api/orders/mine", params={"limit": 1}, headers={"Authorization": authorization}
        )
    except Exception:
        orders = []

    if not orders:
        return {
            "answer": "I don't see any orders on your account yet - once you place one, I can track it here for you.",
            "escalate_to": settings.support_email,
        }

    order = orders[0]
    area = order.get("areaName") or order.get("deliveryCity") or "your chosen pickup area"
    delivered_at = order.get("deliveredAt")
    status = (order.get("deliveryStatus") or "pending").lower()
    if delivered_at:
        answer = f"Your most recent order (#{order['orderId']}) was delivered on {delivered_at} to {area}."
    else:
        answer = (
            f"Your most recent order (#{order['orderId']}) is currently {status}, "
            f"heading to {area}. We'll update this as it moves."
        )
    return {"answer": answer, "escalate_to": settings.support_email}


async def _answer_product_question(lowered_question: str):
    try:
        products = await backend_client.get("/api/products")
    except Exception:
        products = []

    match = None
    for product in products:
        name = (product.get("name") or "").lower()
        product_type = (product.get("type") or "").lower()
        if name and name in lowered_question:
            match = product
            break
        if product_type and product_type in lowered_question:
            match = product
            break
    if not match:
        return None

    label = match.get("name") or match.get("type") or "That product"
    unit = match.get("unit") or "units"
    answer = f"{label} is KES {match.get('price')}, with {match.get('stock')} {unit} in stock."
    if match.get("marketerNote"):
        answer += f" ({match['marketerNote']})"
    return {"answer": answer, "escalate_to": settings.support_email}


@router.post("/email")
async def send_email(request: SupportEmailRequest, authorization: str | None = Header(default=None)):
    """The Help page's "Send us an email" form (see core/support_mail.py).
    A signed-in customer's name and email come from their account, never
    from what the page sends."""
    if not support_mail.configured():
        raise HTTPException(status_code=503, detail="Sending isn't available right now - please use your own email app (link below).")
    form = request.model_dump()
    account = None
    if authorization:
        try:
            account = await backend_client.get("/api/auth/me", headers={"Authorization": authorization})
        except HTTPException:
            account = None  # an expired sign-in counts as not signed in
    try:
        mail = support_mail.prepare(form, account)
        if support_mail.looks_automated(form):
            # Looks like a bot: answer as if sent, send nothing.
            return {"sent": True, "replyTo": mail["email"], "confirmation": False}
        support_mail.check_limits(mail)
    except support_mail.FormProblem as problem:
        raise HTTPException(status_code=problem.status, detail=problem.message) from problem
    try:
        confirmation = await asyncio.to_thread(support_mail.send, mail)
    except (smtplib.SMTPException, OSError) as exc:
        log.warning("support email not sent: %s", type(exc).__name__)
        raise HTTPException(status_code=503, detail="Sending isn't working right now - please use your own email app (link below).") from exc
    return {"sent": True, "replyTo": mail["email"], "confirmation": confirmation, "fullCopy": mail["verified"]}


@router.get("/faqs")
async def faqs():
    """The Help page's frequently asked questions - published ones only,
    in display order; admins keep them up to date from the desktop app."""
    return await backend_client.get("/api/faqs")


@router.get("/contact")
async def contact():
    return {
        "name": settings.support_name,
        "email": settings.support_email,
        "phone": settings.support_phone,
        "whatsapp": settings.support_whatsapp,
        "whatsappLink": whatsapp_link(),
        # For the footer on every page (empty = not shown).
        "tiktok": settings.support_tiktok,
        "instagram": settings.support_instagram,
        # Whether the "Send us an email" form can send (frontend/.env set).
        "emailForm": support_mail.configured(),
    }
