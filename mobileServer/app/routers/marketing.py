from fastapi import APIRouter, Header

from app.core import backend_client
from app.schemas.mobile import CampaignProductsRequest, CreateCampaignRequest

router = APIRouter(prefix="/marketing", tags=["marketing"])


@router.get("/campaigns")
async def list_campaigns():
    """Lets the marketing creation page show existing campaigns, not just
    create new ones."""
    return await backend_client.get("/api/marketing/campaigns")


# Must come before /campaigns/{campaign_id} - FastAPI matches routes in
# declaration order, so "deleted" would otherwise be captured as an id.
@router.get("/campaigns/deleted")
async def list_deleted_campaigns():
    """Admin-only "deleted campaigns" view, same pattern as products."""
    return await backend_client.get("/api/marketing/campaigns/deleted")


@router.get("/campaigns/{campaign_id}")
async def get_campaign(campaign_id: int):
    return await backend_client.get(f"/api/marketing/campaigns/{campaign_id}")


@router.post("/campaigns")
async def create_campaign(request: CreateCampaignRequest, x_actor_user_id: int = Header(...)):
    """Mobile marketing creation page (marketer/admin privilege)."""
    return await backend_client.post(
        "/api/marketing/campaigns", json=request.model_dump(), headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )


@router.patch("/campaigns/{campaign_id}")
async def update_campaign(campaign_id: int, request: CreateCampaignRequest, x_actor_user_id: int = Header(...)):
    return await backend_client.patch(
        f"/api/marketing/campaigns/{campaign_id}",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)}
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


@router.post("/campaigns/{campaign_id}/products/{product_id}")
async def link_product(campaign_id: int, product_id: int):
    return await backend_client.post(f"/api/marketing/campaigns/{campaign_id}/products/{product_id}", json={})


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
