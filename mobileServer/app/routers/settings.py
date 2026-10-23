from typing import Optional

from fastapi import APIRouter, Header
from pydantic import BaseModel

from app.core import backend_client

router = APIRouter(prefix="/settings", tags=["settings"])


class CompanyCardRequest(BaseModel):
    name: str
    motto: Optional[str] = None
    code: Optional[str] = None


def _auth(authorization: Optional[str]) -> Optional[dict]:
    return {"Authorization": authorization} if authorization else None


# The company card on Home (24 October 2026): one copy on the server for every
# phone - "only superadmin edits ... others see the motto". Anyone signed in
# reads it; the backend lets only the SuperAdmin change it.
@router.get("/company")
async def company(authorization: Optional[str] = Header(default=None)):
    return await backend_client.get("/api/settings/company", headers=_auth(authorization))


@router.put("/company")
async def set_company(request: CompanyCardRequest, authorization: Optional[str] = Header(default=None)):
    return await backend_client.put("/api/settings/company", json=request.model_dump(), headers=_auth(authorization))
