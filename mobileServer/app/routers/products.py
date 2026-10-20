from fastapi import APIRouter, Header

from app.core import backend_client
from app.schemas.mobile import CreateProductRequest

router = APIRouter(prefix="/products", tags=["products"])


@router.get("")
async def list_products():
    # The app is for staff, so it sees retired products too (with their mark).
    return await backend_client.get("/api/products", params={"includeRetired": "true"})


# Must come before /{product_id} - FastAPI matches routes in declaration
# order, so "deleted"/"depleted" would otherwise be captured as a product id.
@router.get("/deleted")
async def list_deleted_products():
    """Admin-only "deleted products" view (role check in the client UI) -
    lets an admin bring a soft-deleted product back instead of it just
    disappearing."""
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
async def create_product(request: CreateProductRequest, x_actor_user_id: int = Header(...)):
    """Mobile products creation page (marketer/admin/worker privilege -
    role enforcement is a TODO pending real auth; today any caller that
    knows a user id can claim to be that actor)."""
    return await backend_client.post(
        "/api/products", json=request.model_dump(), headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )


@router.patch("/{product_id}")
async def update_product(product_id: int, request: CreateProductRequest, x_actor_user_id: int = Header(...)):
    """Same form as create, reused for edits - including swapping the
    image key after a re-upload via /uploads/products."""
    return await backend_client.patch(
        f"/api/products/{product_id}", json=request.model_dump(), headers={"X-Actor-User-Id": str(x_actor_user_id)}
    )


@router.delete("/{product_id}")
async def delete_product(product_id: int, x_actor_user_id: int = Header(...)):
    """Soft delete with confirmation on the client side - the row stays,
    marked Is_Deleted/Deleted_By/Deleted_At, recoverable via restore."""
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
