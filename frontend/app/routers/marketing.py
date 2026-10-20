from datetime import date, datetime
from zoneinfo import ZoneInfo

from fastapi import APIRouter

from app.core import backend_client

router = APIRouter(prefix="/marketing", tags=["marketing"])

KENYA = ZoneInfo("Africa/Nairobi")


def _is_live(campaign: dict, today: date) -> bool:
    # Same rule the backend's AI assistant uses for "promotions running
    # now" (AiChatService.buildPromotionContext): Active, and today inside
    # its dates when it has any.
    if (campaign.get("status") or "").lower() != "active":
        return False
    start, end = campaign.get("startAt"), campaign.get("endAt")
    if start and date.fromisoformat(start[:10]) > today:
        return False
    if end and date.fromisoformat(end[:10]) < today:
        return False
    return True


@router.get("/campaigns")
async def list_campaigns():
    """Featured page and the Products page banner: only what a customer
    should see. Drafts, paused or ended campaigns, and campaigns with no
    product to buy, never leave this server ("hide drafts and the internal
    details"), and the staff-only fields (status, channel, start date) are
    left out. Each campaign carries its products' names, not bare ids."""
    today = datetime.now(KENYA).date()
    campaigns = [c for c in await backend_client.get("/api/marketing/campaigns") if _is_live(c, today)]
    if not campaigns:
        return []
    # Deleted products are not in this list, so links to them drop out too.
    names = {p["productId"]: p.get("name") for p in await backend_client.get("/api/products")}
    featured = []
    for campaign in campaigns:
        product_ids = await backend_client.get(f"/api/marketing/campaigns/{campaign['campaignId']}/products")
        products = [{"productId": pid, "name": names[pid]} for pid in product_ids if pid in names]
        if not products:
            continue
        featured.append({
            "campaignId": campaign["campaignId"],
            "name": campaign.get("name"),
            "media": campaign.get("media"),
            "mediaType": campaign.get("mediaType"),
            "endAt": campaign.get("endAt"),
            "products": products,
        })
    return featured
