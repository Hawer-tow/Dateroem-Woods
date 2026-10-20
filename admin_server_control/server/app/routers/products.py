from fastapi import APIRouter, Header

from app.core import backend_client
from app.schemas.admin import ProductRequest

router = APIRouter(prefix="/products", tags=["products"])


@router.get("")
async def list_products(limit: int | None = None):
    # The desktop app is for admins, so it sees retired products (with mark).
    params = {"includeRetired": "true"}
    if limit is not None:
        params["limit"] = limit
    return await backend_client.get("/api/products", params=params)


@router.get("/deleted")
async def list_deleted_products():
    return await backend_client.get("/api/products/deleted")


@router.get("/depleted")
async def list_depleted_products():
    """Products that have run out but are still live - the "needs attention"
    list where an admin can retire one when its stock is depleted."""
    return await backend_client.get("/api/products/depleted")


@router.get("/{product_id}")
async def get_product(product_id: int):
    return await backend_client.get(f"/api/products/{product_id}", params={"includeRetired": "true"})


@router.post("")
async def create_product(request: ProductRequest, x_actor_user_id: int = Header(...)):
    return await backend_client.post(
        "/api/products", json=request.model_dump(), headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )


@router.patch("/{product_id}")
async def update_product(product_id: int, request: ProductRequest, x_actor_user_id: int = Header(...)):
    return await backend_client.patch(
        f"/api/products/{product_id}", json=request.model_dump(), headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )


@router.delete("/{product_id}")
async def delete_product(product_id: int, x_actor_user_id: int = Header(...)):
    await backend_client.delete(f"/api/products/{product_id}", headers={"X-Actor-User-Id": str(x_actor_user_id)})
    return {"status": "deleted"}


@router.post("/{product_id}/restore")
async def restore_product(product_id: int, x_actor_user_id: int = Header(...)):
    return await backend_client.post(
        f"/api/products/{product_id}/restore", json={}, headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )


@router.post("/{product_id}/retire")
async def retire_product(product_id: int, x_actor_user_id: int = Header(...)):
    """Hide a product from the shop (no new orders); existing orders and its
    stock are untouched, and it can be brought back."""
    return await backend_client.post(
        f"/api/products/{product_id}/retire", json={}, headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )


@router.post("/{product_id}/unretire")
async def unretire_product(product_id: int, x_actor_user_id: int = Header(...)):
    return await backend_client.post(
        f"/api/products/{product_id}/unretire", json={}, headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )
