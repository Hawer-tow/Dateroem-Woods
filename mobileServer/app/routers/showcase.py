from fastapi import APIRouter
from pydantic import BaseModel, Field

from app.core import backend_client

# The Bulk Orders page's showcase pictures (backend ShowcaseService): "a
# marketer admin or superadmin can edit it". The backend checks the role
# from the phone's sign-in token on every change.
router = APIRouter(prefix="/showcase", tags=["showcase"])


class ShowcasePicture(BaseModel):
    # The key /uploads/showcase returned ("showcase/....jpg"); unused on a
    # caption edit.
    image: str | None = None
    caption: str | None = Field(default=None, max_length=120)


class ShowcaseOrder(BaseModel):
    showcaseIds: list[int]


@router.get("")
async def list_pictures():
    return await backend_client.get("/api/showcase")


@router.post("")
async def add_picture(request: ShowcasePicture):
    return await backend_client.post("/api/showcase", json=request.model_dump())


# Before /{showcase_id} so "order" isn't read as an id.
@router.put("/order")
async def reorder_pictures(request: ShowcaseOrder):
    return await backend_client.put("/api/showcase/order", json=request.model_dump())


@router.patch("/{showcase_id}")
async def edit_caption(showcase_id: int, request: ShowcasePicture):
    return await backend_client.patch(f"/api/showcase/{showcase_id}", json={"caption": request.caption})


@router.delete("/{showcase_id}")
async def remove_picture(showcase_id: int):
    await backend_client.delete(f"/api/showcase/{showcase_id}")
    return {"status": "removed"}
