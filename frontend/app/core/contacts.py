from app.core.config import settings


def whatsapp_link() -> str:
    """The business WhatsApp as a wa.me link. wa.me needs 254-prefixed
    digits only (no leading 0, no +) - same normalization convention as
    MpesaClient.normalizePhone on the backend, so SUPPORT_WHATSAPP may be
    written either way ("0712345678" or "+254712345678"). Used by the Help
    page, the footer and the Bulk Orders page."""
    digits = "".join(ch for ch in settings.support_whatsapp if ch.isdigit())
    if digits.startswith("0"):
        digits = "254" + digits[1:]
    return f"https://wa.me/{digits}"
