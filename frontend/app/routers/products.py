from fastapi import APIRouter, Query

from app.core import backend_client

router = APIRouter(prefix="/products", tags=["products"])


@router.get("")
async def list_products():
    """Product view page: users browse what's in the database. Actual
    "most ordered / buy now" ranking comes from the backend's
    fn_product_order_count-backed /popular endpoint, not computed here."""
    return await backend_client.get("/api/products")


@router.get("/popular")
async def popular_products(limit: int = Query(default=10)):
    return await backend_client.get("/api/products/popular", params={"limit": limit})


@router.get("/{product_id}")
async def get_product(product_id: int):
    return await backend_client.get(f"/api/products/{product_id}")
