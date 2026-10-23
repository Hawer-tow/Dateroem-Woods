from fastapi import APIRouter, Header
from pydantic import BaseModel

from app.core import backend_client
from app.core.auth_header import auth_header

router = APIRouter(prefix="/workers", tags=["workers"])


class FieldRequest(BaseModel):
    label: str
    kind: str = "text"
    hint: str | None = None
    sensitive: bool = False
    required: bool = False


class DocTypeRequest(BaseModel):
    label: str
    hint: str | None = None
    required: bool = False


class ActiveRequest(BaseModel):
    active: bool


class VerifyRequest(BaseModel):
    status: str
    note: str | None = None


# Worker details and documents (migration 040). Admins see where each worker
# stands; opening the numbers and photos, verifying, deleting and adding
# fields are the SuperAdmin's - the backend checks every one.
@router.get("")
async def list_workers(authorization: str | None = Header(default=None)):
    return await backend_client.get("/api/workers", headers=auth_header(authorization))


@router.get("/definitions")
async def definitions(authorization: str | None = Header(default=None)):
    return await backend_client.get("/api/workers/definitions", headers=auth_header(authorization))


@router.post("/fields")
async def add_field(request: FieldRequest, authorization: str | None = Header(default=None)):
    return await backend_client.post("/api/workers/fields", json=request.model_dump(), headers=auth_header(authorization))


@router.patch("/fields/{field_key}")
async def set_field_active(field_key: str, request: ActiveRequest, authorization: str | None = Header(default=None)):
    return await backend_client.patch(f"/api/workers/fields/{field_key}", json=request.model_dump(), headers=auth_header(authorization))


@router.post("/doc-types")
async def add_doc_type(request: DocTypeRequest, authorization: str | None = Header(default=None)):
    return await backend_client.post("/api/workers/doc-types", json=request.model_dump(), headers=auth_header(authorization))


@router.patch("/doc-types/{type_key}")
async def set_doc_type_active(type_key: str, request: ActiveRequest, authorization: str | None = Header(default=None)):
    return await backend_client.patch(f"/api/workers/doc-types/{type_key}", json=request.model_dump(), headers=auth_header(authorization))


@router.get("/{user_id}")
async def worker_detail(user_id: int, authorization: str | None = Header(default=None)):
    return await backend_client.get(f"/api/workers/{user_id}", headers=auth_header(authorization))


@router.get("/{user_id}/documents/{document_id}")
async def worker_document(user_id: int, document_id: int, authorization: str | None = Header(default=None)):
    return await backend_client.get(f"/api/workers/{user_id}/documents/{document_id}", headers=auth_header(authorization))


@router.post("/{user_id}/verify")
async def verify_worker(user_id: int, request: VerifyRequest, authorization: str | None = Header(default=None)):
    return await backend_client.post(f"/api/workers/{user_id}/verify", json=request.model_dump(), headers=auth_header(authorization))


@router.delete("/{user_id}/documents/{document_id}")
async def delete_worker_document(user_id: int, document_id: int, authorization: str | None = Header(default=None)):
    await backend_client.delete(f"/api/workers/{user_id}/documents/{document_id}", headers=auth_header(authorization))
    return {"status": "deleted"}


@router.delete("/{user_id}")
async def delete_worker_details(user_id: int, authorization: str | None = Header(default=None)):
    await backend_client.delete(f"/api/workers/{user_id}", headers=auth_header(authorization))
    return {"status": "deleted"}
