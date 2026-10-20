from fastapi import APIRouter

from app.core import backend_client, support_mail
from app.core.config import settings
from app.core.contacts import whatsapp_link

router = APIRouter(prefix="/heavy-orders", tags=["heavy-orders"])


@router.get("/contact")
async def contact():
    """Heavy order page: big/heavy orders (with their own permit and
    certification concerns) aren't self-served through the cart - the
    customer is handed straight to a human via WhatsApp/phone/email so an
    admin can negotiate and finalize it (recorded on the mobile app as a
    BULK_DELIVERY once agreed)."""
    return {
        "whatsapp": settings.support_whatsapp,
        # Built here like the Help page's: the page used to glue the raw
        # number onto wa.me, which breaks for a 0... number.
        "whatsappLink": whatsapp_link(),
        "phone": settings.support_phone,
        # The orders desk (+orders), not support - bulk enquiries have their own inbox.
        "email": settings.orders_email,
        "name": settings.orders_name,
        # Whether the email button can open the "Send us an email" window
        # (sent through /help/email; frontend/.env set).
        "emailForm": support_mail.configured(),
    }


@router.get("/showcase")
async def showcase():
    """The Bulk Orders page's slideshow: "pictures that can be used to show
    case what bulk orders look like" - in order, with optional captions.
    Managed by Marketers and admins in the phone and desktop apps."""
    return await backend_client.get("/api/showcase")
