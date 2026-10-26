from fastapi import APIRouter, Header

from app.core import backend_client
from app.schemas.admin import CampaignProductsRequest, CampaignRequest

router = APIRouter(prefix="/marketing", tags=["marketing"])


@router.get("/campaigns")
async def list_campaigns(limit: int | None = None, includeRetired: bool = False):
    """includeRetired: the Marketing list asks for retired campaigns too
    (marked); a desktop copy from before 26 October 2026 doesn't, and so
    never shows one as if it were on the website."""
    params = {}
    if limit is not None:
        params["limit"] = limit
    if includeRetired:
        params["includeRetired"] = "true"
    return await backend_client.get("/api/marketing/campaigns", params=params or None)


@router.get("/campaigns/deleted")
async def list_deleted_campaigns():
    return await backend_client.get("/api/marketing/campaigns/deleted")


@router.get("/campaigns/{campaign_id}")
async def get_campaign(campaign_id: int):
    return await backend_client.get(f"/api/marketing/campaigns/{campaign_id}")


@router.post("/campaigns")
async def create_campaign(request: CampaignRequest, x_actor_user_id: int = Header(...)):
    return await backend_client.post(
        "/api/marketing/campaigns", json=request.model_dump(), headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )


@router.patch("/campaigns/{campaign_id}")
async def update_campaign(campaign_id: int, request: CampaignRequest, x_actor_user_id: int = Header(...)):
    return await backend_client.patch(
        f"/api/marketing/campaigns/{campaign_id}",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
    )


@router.delete("/campaigns/{campaign_id}")
async def delete_campaign(campaign_id: int, x_actor_user_id: int = Header(...)):
    await backend_client.delete(
        f"/api/marketing/campaigns/{campaign_id}", headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )
    return {"status": "deleted"}


@router.post("/campaigns/{campaign_id}/restore")
async def restore_campaign(campaign_id: int, x_actor_user_id: int = Header(...)):
    return await backend_client.post(
        f"/api/marketing/campaigns/{campaign_id}/restore", json={}, headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )


# "allow a campaign to be retired just like the products" - off the website,
# kept, and can be brought back (Marketer/Admin/SuperAdmin, checked by the
# backend).
@router.post("/campaigns/{campaign_id}/retire")
async def retire_campaign(campaign_id: int, x_actor_user_id: int = Header(...)):
    return await backend_client.post(
        f"/api/marketing/campaigns/{campaign_id}/retire", json={}, headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )


@router.post("/campaigns/{campaign_id}/unretire")
async def unretire_campaign(campaign_id: int, x_actor_user_id: int = Header(...)):
    return await backend_client.post(
        f"/api/marketing/campaigns/{campaign_id}/unretire", json={}, headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )


@router.get("/campaigns/{campaign_id}/products")
async def campaign_products(campaign_id: int):
    """The ids of the products a campaign promotes, ticked in its form."""
    return await backend_client.get(f"/api/marketing/campaigns/{campaign_id}/products")


@router.put("/campaigns/{campaign_id}/products")
async def set_campaign_products(campaign_id: int, request: CampaignProductsRequest, x_actor_user_id: int = Header(...)):
    """The "Products in this campaign" list - the whole chosen set at once."""
    return await backend_client.put(
        f"/api/marketing/campaigns/{campaign_id}/products",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
    )
