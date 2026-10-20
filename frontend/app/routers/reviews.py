from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel, Field

from app.core import backend_client

router = APIRouter(prefix="/reviews", tags=["reviews"])


class SaveReviewRequest(BaseModel):
    rating: int = Field(ge=1, le=5)
    comment: str | None = Field(default=None, max_length=500)
    make_public: bool = False
    # The agreement version the customer ticked "I agree" to - required by
    # the backend before a rating can be made public.
    consent_version: str | None = Field(default=None, max_length=20)


def _require_sign_in(authorization: str | None) -> dict:
    if not authorization:
        raise HTTPException(status_code=401, detail="Sign in required")
    return {"Authorization": authorization}


@router.get("/summary")
async def summaries():
    """Average + count per product (only products rated by enough buyers)
    for the product cards."""
    return await backend_client.get("/api/reviews/summary")


@router.get("/product/{product_id}")
async def product_reviews(product_id: int):
    """A product page's ratings: nothing until enough buyers have rated,
    then the average, star breakdown and the reviews customers agreed to
    make public (first name + initial only)."""
    return await backend_client.get(f"/api/reviews/product/{product_id}")


@router.get("/mine")
async def my_reviews(authorization: str | None = Header(default=None)):
    """Delivered goods still waiting for a rating, and the customer's own
    ratings (always editable, e.g. to make one private again)."""
    return await backend_client.get("/api/reviews/mine", headers=_require_sign_in(authorization))


@router.put("/product/{product_id}")
async def save_review(product_id: int, request: SaveReviewRequest, authorization: str | None = Header(default=None)):
    payload = {
        "rating": request.rating,
        "comment": request.comment,
        "makePublic": request.make_public,
        "consentVersion": request.consent_version,
    }
    return await backend_client.put(
        f"/api/reviews/product/{product_id}", json=payload, headers=_require_sign_in(authorization)
    )


@router.delete("/product/{product_id}")
async def delete_review(product_id: int, authorization: str | None = Header(default=None)):
    return await backend_client.delete(f"/api/reviews/product/{product_id}", headers=_require_sign_in(authorization))
